package de.saar.coli.amtools.script;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.basic.Pair;
import de.saar.coli.amrtagging.AlignedAMDependencyTree;
import de.saar.coli.amrtagging.AmConllEntry;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.formalisms.sdp.dm.DMBlobUtils;
import de.saar.coli.amrtagging.formalisms.sdp.pas.PASBlobUtils;
import de.saar.coli.amrtagging.formalisms.sdp.psd.PSDBlobUtils;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.util.Counter;
import de.up.ling.tree.ParseException;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.eclipse.collections.impl.factory.Sets;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;
import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;

public class HeadAndConstituentAnalysis {

    //SDP corpora (i.e. original graphs)
    @Parameter(names = {"--corpusDM", "-dm"}, description = "Path to the input corpus (en.dm.sdp) or subset thereof")
    private String corpusPathDM = "../../data/corpora/semDep/sdp2014_2015/data/2015/en.dm.sdp";

    @Parameter(names = {"--corpusPAS", "-pas"}, description = "Path to the input corpus (en.pas.sdp) or subset thereof")
    private String corpusPathPAS = "../../data/corpora/semDep/sdp2014_2015/data/2015/en.pas.sdp";

    @Parameter(names = {"--corpusPSD", "-psd"}, description = "Path to the input corpus (en.psd.sdp) or subset thereof")
    private String corpusPathPSD = "../../data/corpora/semDep/sdp2014_2015/data/2015/en.psd.sdp";

    // amconll files (i.e. AM dependency trees)
    @Parameter(names = {"--amconllDM", "-amdm"}, description = "Path to the input corpus (.amconll) or subset thereof")
    private String amconllPathDM = "../../data/corpora/semDep/uniformify2020/original_decompositions/dm/gold-dev/gold-dev.amconll";

    @Parameter(names = {"--amconllPAS", "-ampas"}, description = "Path to the input corpus (.amconll) or subset thereof")
    private String amconllPathPAS = "../../data/corpora/semDep/uniformify2020/original_decompositions/pas/gold-dev/gold-dev.amconll";

    @Parameter(names = {"--amconllPSD", "-ampsd"}, description = "Path to the input corpus (.amconll) or subset thereof")
    private String amconllPathPSD = "../../data/corpora/semDep/uniformify2020/original_decompositions/psd/gold-dev/gold-dev.amconll";


    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;


    private static DMBlobUtils dmBlobUtils = new DMBlobUtils();
    private static PASBlobUtils pasBlobUtils = new PASBlobUtils();
    private static PSDBlobUtils psdBlobUtils = new PSDBlobUtils();

    /**
     * @param args
     * @throws FileNotFoundException
     * @throws IOException
     * @throws ParseException
     * @throws ParserException
     * @throws AlignedAMDependencyTree.ConllParserException
     */
    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException {
        //just getting command line args
        HeadAndConstituentAnalysis cli = new HeadAndConstituentAnalysis();
        JCommander commander = new JCommander(cli);
        try {
            commander.parse(args);
        } catch (com.beust.jcommander.ParameterException ex) {
            System.err.println("An error occured: " + ex.toString());
            System.err.println("\n Available options: ");
            commander.usage();
            return;
        }
        if (cli.help) {
            commander.usage();
            return;
        }


        //setup
        GraphReader2015 grDM = new GraphReader2015(cli.corpusPathDM);
        GraphReader2015 grPAS = new GraphReader2015(cli.corpusPathPAS);
        GraphReader2015 grPSD = new GraphReader2015(cli.corpusPathPSD);
        Graph dmGraph;
        Graph pasGraph;
        Graph psdGraph;
        List<AmConllSentence> amDM = AmConllSentence.read(new FileReader(cli.amconllPathDM));
        List<AmConllSentence> amPSD = AmConllSentence.read(new FileReader(cli.amconllPathPSD));
        List<AmConllSentence> amPAS = AmConllSentence.read(new FileReader(cli.amconllPathPAS));
        // map IDs to AmConllSentences so we can look the AmConllSentences up
        Map<String, AmConllSentence> id2amDM = new HashMap<>();
        amDM.stream().forEach(sent -> id2amDM.put(sent.getId(), sent));
        Map<String, AmConllSentence> id2amPAS = new HashMap<>();
        amPAS.stream().forEach(sent -> id2amPAS.put(sent.getId(), sent));
        Map<String, AmConllSentence> id2amPSD = new HashMap<>();
        amPSD.stream().forEach(sent -> id2amPSD.put(sent.getId(), sent));
        Set<String> decomposedIDs = Sets.intersectAll(id2amDM.keySet(), id2amPAS.keySet(), id2amPSD.keySet());

        int totalCoord = 0;
        int psdPasCompleteMatchCoord = 0;
        int completeDMMatchCoord = 0;
        int someDMMatchCoord = 0;
        Counter<Integer> genereousSubtreeCheckEdgeCounterCoord = new Counter<>();
        Counter<Integer> constituencyMatchEdgeCounterCoord = new Counter<>();
        Counter<Integer> headMatchEdgeCounterCoord = new Counter<>();

        int totalPrep = 0;
        int dmPasCompleteMatchPrep = 0;
        int completePSDMatchPrep = 0;
        int dmPasHeadMatchPrep = 0;
        int dmPasRightMatchPrep = 0;
        int somePSDMatchPrep = 0;
        Counter<Integer> genereousSubtreeCheckEdgeCounterPrep = new Counter<>();
        Counter<Integer> constituencyMatchEdgeCounterPrep = new Counter<>();
        Counter<Integer> headMatchEdgeCounterPrep = new Counter<>();

        while ((dmGraph = grDM.readGraph()) != null && (pasGraph = grPAS.readGraph()) != null && (psdGraph = grPSD.readGraph()) != null) {
            if (decomposedIDs.contains(dmGraph.id)) {
                //now we know the graph was decomposed in all graphbanks, and we have all three AM dep trees for it.
                String id = dmGraph.id;
                AmConllSentence dmDep = id2amDM.get(id);
                AmConllSentence pasDep = id2amPAS.get(id);
                AmConllSentence psdDep = id2amPSD.get(id);
                //ignore 0 in next loop, since it is the artificial root of the SDP graph
                for (int i = 1; i < psdGraph.getNNodes(); i++) {
                    if (isCoordWithTwoChildren(dmDep, pasDep, psdDep, i)) {
                        totalCoord++;
                        Pair<Integer, Integer> pasChildren = getLeftAndRightChildId(pasDep, i);
                        Pair<Integer, Integer> psdChildren = getLeftAndRightChildId(psdDep, i);
                        if (pasChildren.equals(psdChildren)) {
                            psdPasCompleteMatchCoord++;
                            if (hasEdgeBetween(dmDep, psdChildren.left, psdChildren.right)) {
                                completeDMMatchCoord++;
                            }
                        }
                        if (hasEdgeBetween(dmDep, Arrays.asList(pasChildren.left, psdChildren.left),
                                Arrays.asList(pasChildren.right, psdChildren.right))) {
                            someDMMatchCoord++;
                        }
                        genereousSubtreeCheckEdgeCounterCoord.add(getGenerousSubtreeEdgeCount(dmDep, pasDep, psdDep,
                                pasChildren.left, pasChildren.right, psdChildren.left, psdChildren.right));
                        constituencyMatchEdgeCounterCoord.add(getConstituentMatchEdgeCount(dmDep, pasDep, psdDep,
                                pasChildren.left, pasChildren.right, psdChildren.left, psdChildren.right));
                        headMatchEdgeCounterCoord.add(getHeadMatchEdgeCount(psdDep, pasDep, dmDep,
                                pasChildren.left, pasChildren.right, psdChildren.left, psdChildren.right));
                    }

                    if (isDMPASPreposition(dmDep, pasDep, psdDep, i)) {
                        totalPrep++;
                        Pair<Integer, Integer> dmChildren = getHeadAndChild(dmDep, i);
                        Pair<Integer, Integer> pasChildren = getHeadAndChild(pasDep, i);
                        if (pasChildren.equals(dmChildren)) {
                            dmPasCompleteMatchPrep++;
                            if (hasEdgeBetween(psdDep, dmChildren.left, dmChildren.right)) {
                                completePSDMatchPrep++;
                            }
                        }
                        if (dmChildren.left.equals(pasChildren.left)) {
                            dmPasHeadMatchPrep++;
                        }
                        if (dmChildren.right.equals(pasChildren.right)) {
                            dmPasRightMatchPrep++;
                        }
                        if (hasEdgeBetween(psdDep, Arrays.asList(pasChildren.left, dmChildren.left),
                                Arrays.asList(pasChildren.right, dmChildren.right))) {
                            somePSDMatchPrep++;
                        }
                        genereousSubtreeCheckEdgeCounterPrep.add(getGenerousSubtreeEdgeCount(psdDep, pasDep, dmDep,
                                pasChildren.left, pasChildren.right, dmChildren.left, dmChildren.right));
                        constituencyMatchEdgeCounterPrep.add(getConstituentMatchEdgeCount(psdDep, pasDep, dmDep,
                                pasChildren.left, pasChildren.right, dmChildren.left, dmChildren.right));
                        headMatchEdgeCounterPrep.add(getHeadMatchEdgeCount(psdDep, pasDep, dmDep,
                                pasChildren.left, pasChildren.right, dmChildren.left, dmChildren.right));
                    }
                }
            }
        }

        System.err.println("Total coord: "+totalCoord);
        System.err.println("complete PSD PAS head match: "+psdPasCompleteMatchCoord);
        System.err.println(psdPasCompleteMatchCoord/(float)totalCoord);
        System.err.println("complete DM also matches: "+completeDMMatchCoord);
        System.err.println(completeDMMatchCoord/(float)totalCoord);
        System.err.println("DM heads contained: "+someDMMatchCoord);
        System.err.println(someDMMatchCoord/(float)totalCoord);
        System.err.println("subtree edge counts:");
        genereousSubtreeCheckEdgeCounterCoord.printAllSorted();
        System.err.println("constituency match edge counts:");
        constituencyMatchEdgeCounterCoord.printAllSorted();
        System.err.println("head match edge counts:");
        headMatchEdgeCounterCoord.printAllSorted();
        System.err.println();
        System.err.println("Total Prep: "+totalPrep);
        System.err.println("complete DM PAS head match: "+dmPasCompleteMatchPrep);
        System.err.println(dmPasCompleteMatchPrep/(float)totalPrep);
        System.err.println("complete PSD also matches: "+completePSDMatchPrep);
        System.err.println(completePSDMatchPrep/(float)totalPrep);
        System.err.println("DM PAS head (left) head match: "+dmPasHeadMatchPrep);
        System.err.println(dmPasHeadMatchPrep/(float)totalPrep);
        System.err.println("DM PAS right head match: "+dmPasRightMatchPrep);
        System.err.println(dmPasRightMatchPrep/(float)totalPrep);
        System.err.println("PSD heads contained: "+somePSDMatchPrep);
        System.err.println(somePSDMatchPrep/(float)totalPrep);
        System.err.println("subtree edge counts:");
        genereousSubtreeCheckEdgeCounterPrep.printAllSorted();
        System.err.println("constituency match edge counts:");
        constituencyMatchEdgeCounterPrep.printAllSorted();
        System.err.println("head match edge counts:");
        headMatchEdgeCounterPrep.printAllSorted();

    }


    private static boolean isCoordWithTwoChildren(AmConllSentence dmDep, AmConllSentence pasDep, AmConllSentence psdDep, int i) {
        boolean dm = dmDep.get(i-1).getEdgeLabel().equals("IGNORE");
        boolean isCC = dmDep.get(i-1).getPos().equals("CC");
        boolean pas = pasDep.getChildren(i-1).size() == 2;
        boolean psd = psdDep.getChildren(i-1).size() == 2;
        return dm && isCC && psd && pas;
    }

    private static boolean isDMPASPreposition(AmConllSentence dmDep, AmConllSentence pasDep, AmConllSentence psdDep, int i) {
        boolean dm = dmDep.getChildren(i-1).size() == 1 && dmDep.get(i-1).getEdgeLabel().startsWith(ApplyModifyGraphAlgebra.OP_MODIFICATION);
        boolean isPrep = dmDep.get(i-1).getPos().equals("IN") || dmDep.get(i-1).getPos().equals("TO");
        boolean pas = pasDep.getChildren(i-1).size() == 1 && pasDep.get(i-1).getEdgeLabel().startsWith(ApplyModifyGraphAlgebra.OP_MODIFICATION);
        boolean psd = psdDep.get(i-1).getEdgeLabel().equals("IGNORE");
        return dm && isPrep && psd && pas;
    }

    private static Pair<Integer, Integer> getLeftAndRightChildId(AmConllSentence dep, int i) {
        List<AmConllEntry> children = dep.getChildren(i-1);
        if (!(children.size() == 2)) {
            throw new IllegalArgumentException();
        }
        return new Pair(children.get(0).getId(), children.get(1).getId());
    }

    private static Pair<Integer, Integer> getHeadAndChild(AmConllSentence dep, int i) {
        List<AmConllEntry> children = dep.getChildren(i-1);
        if (!(children.size() == 1)) {
            throw new IllegalArgumentException();
        }
        return new Pair(dep.get(i-1).getHead(), children.get(0).getId());
    }

    private static boolean hasEdgeBetween(AmConllSentence dep, int id1, int id2) {
        return hasEdgeBetween(dep, Collections.singleton(id1), Collections.singleton(id2));
    }

    private static boolean hasEdgeBetween(AmConllSentence dep, Collection<Integer> ids1, Collection<Integer> ids2) {
        for (Integer i : ids1) {
            for (Integer j : ids2) {
                if (dep.get(i-1).getHead() == j || dep.get(j-1).getHead() == i) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int getGenerousSubtreeEdgeCount(AmConllSentence dep1, AmConllSentence dep2, AmConllSentence dep3,
                                                   int left2, int right2, int left3, int right3) {
        int ret = 0;
        for (AmConllEntry word : dep1) {
            int id = word.getId();
            int head = word.getHead();
            if (edgeSatisfiesAny(dep1, dep2, dep3, id, head, left2, right2, left3, right3,
                    d1 -> d2 -> i1 -> i2 -> symmetricSubtreeContainmentCheck(d1, i1, d2, i2))) {
               ret++;
            }
        }
        return ret;
    }

    private static boolean edgeSatisfiesAny(AmConllSentence dep1, AmConllSentence dep2, AmConllSentence dep3,
                                            int id, int head, int left2, int right2, int left3, int right3,
                                            Function<AmConllSentence, Function<AmConllSentence, Function<Integer, Function<Integer, Boolean>>>> function) {
        int left1 = Math.min(id, head);
        int right1 = Math.max(id, head);
        boolean leftMatch = function.apply(dep1).apply(dep2).apply(left1).apply(left2)
                || function.apply(dep1).apply(dep3).apply(left1).apply(left3);
        boolean rightMatch = function.apply(dep1).apply(dep2).apply(right1).apply(right2)
                || function.apply(dep1).apply(dep3).apply(right1).apply(right3);
        return leftMatch && rightMatch;
    }


    private static int getConstituentMatchEdgeCount(AmConllSentence dep1, AmConllSentence dep2, AmConllSentence dep3,
                                                   int left2, int right2, int left3, int right3) {
        int ret = 0;
        for (AmConllEntry word : dep1) {
            int id = word.getId();
            int head = word.getHead();
            if (edgeSatisfiesAny(dep1, dep2, dep3, id, head, left2, right2, left3, right3,
                    d1 -> d2 -> i1 -> i2 -> constituentIdentityCheck(d1, i1, d2, i2))) {
                ret++;
            }
        }
        return ret;
    }


    private static int getHeadMatchEdgeCount(AmConllSentence dep1, AmConllSentence dep2, AmConllSentence dep3,
                                                    int left2, int right2, int left3, int right3) {
        int ret = 0;
        for (AmConllEntry word : dep1) {
            int id = word.getId();
            int head = word.getHead();
            if (edgeSatisfiesAny(dep1, dep2, dep3, id, head, left2, right2, left3, right3,
                    d1 -> d2 -> i1 -> i2 -> (i1 == i2))) {
                ret++;
            }
        }
        return ret;
    }


    /**
     * i is in dep1, j is in dep2, checks if subtree of i contains j or subtree of j contains i.
     * @param dep1
     * @param i
     * @param dep2
     * @param j
     * @return
     */
    private static boolean symmetricSubtreeContainmentCheck(AmConllSentence dep1, int i, AmConllSentence dep2, int j) {
        return subtreeContains(dep1, i, j) || subtreeContains(dep2, j, i);
    }

    private static boolean constituentIdentityCheck(AmConllSentence dep1, int i, AmConllSentence dep2, int j) {
        return getConstituent(dep1, i).equals(getConstituent(dep2, j));
    }

    private static boolean subtreeContains(AmConllSentence dep, int subtreeHead, int doesItContainThis) {
        return getSubtreeIds(dep, subtreeHead).contains(doesItContainThis);
    }

    static IntSet getSubtreeIds(AmConllSentence dep, int subtreeHead) {
        IntSet ret = new IntOpenHashSet();
        ret.add(subtreeHead);
        for (AmConllEntry child : dep.getChildren(subtreeHead-1)) {
            ret.addAll(getSubtreeIds(dep, child.getId()));
        }
        return ret;
    }


    static Pair<Integer, Integer> getConstituent(AmConllSentence dep, int id) {
        IntSet subtreeIds = getSubtreeIds(dep, id);
        int start = subtreeIds.stream().min(Comparator.naturalOrder()).get();
        int end = subtreeIds.stream().max(Comparator.naturalOrder()).get()+1;
        return new Pair<>(start, end);
    }
}
