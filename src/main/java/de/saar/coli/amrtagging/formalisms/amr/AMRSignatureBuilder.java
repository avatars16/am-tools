/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.amr;

import com.google.common.collect.Sets;
import de.saar.basic.Pair;
import de.saar.coli.amrtagging.Alignment;
import de.saar.coli.amrtagging.formalisms.AMSignatureBuilder;
import de.saar.coli.amrtagging.formalisms.GeneralBlobUtils;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.AMDecompositionAutomaton;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.GraphAlgebra;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import static de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra.GRAPH_TYPE_SEP;
import static de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra.OP_COREFMARKER;
import de.up.ling.irtg.automata.TreeAutomaton;
import de.up.ling.irtg.corpus.CorpusReadingException;
import de.up.ling.irtg.signature.Signature;
import de.up.ling.irtg.util.TupleIterator;
import de.up.ling.irtg.util.Util;
import de.up.ling.tree.ParseException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 *
 * @author Jonas
 */
public class AMRSignatureBuilder implements AMSignatureBuilder{

    
    //---------------------------------------------   constants   --------------------------------------------------------
    

    
    protected Collection<Function<Map<GraphEdge, String>,Collection<Map<GraphEdge, String>>>> lexiconSourceRemappingsMulti;
    protected Collection<Function<Map<GraphEdge, String>,Collection<Map<GraphEdge, String>>>> lexiconSourceRemappingsOne;
    private static final boolean allowOriginalSources = true;
    
    
    public AMRBlobUtils blobUtils = new AMRBlobUtils();
    
     {
        // control which source renamings are allowed
        lexiconSourceRemappingsMulti = new ArrayList<>();
        lexiconSourceRemappingsOne = new ArrayList<>();
        //allowOriginalSources  = true;
        lexiconSourceRemappingsMulti.add(map -> promoteObj(map, 2));
        lexiconSourceRemappingsMulti.add(map -> promoteObj(map, 3));
        lexiconSourceRemappingsMulti.add(map -> promoteObj(map, 4));
        lexiconSourceRemappingsMulti.add(map -> promoteObj(map, 5));
        lexiconSourceRemappingsMulti.add(map -> promoteObj(map, 6));
        lexiconSourceRemappingsMulti.add(map -> promoteObj(map, 7));
        lexiconSourceRemappingsMulti.add(map -> promoteObj(map, 8));
        lexiconSourceRemappingsMulti.add(map -> promoteObj(map, 9));
        lexiconSourceRemappingsOne.add(map -> passivize(map, 1));
        lexiconSourceRemappingsOne.add(map -> passivize(map, 2));
        lexiconSourceRemappingsOne.add(map -> passivize(map, 3));
        lexiconSourceRemappingsOne.add(map -> passivize(map, 4));
        lexiconSourceRemappingsOne.add(map -> passivize(map, 5));
        lexiconSourceRemappingsOne.add(map -> passivize(map, 6));
        lexiconSourceRemappingsOne.add(map -> passivize(map, 7));
        lexiconSourceRemappingsOne.add(map -> passivize(map, 8));
        lexiconSourceRemappingsOne.add(map -> passivize(map, 9));
//        lexiconSourceRemappingsOne.add(map -> Collections.singleton(promoteObjMax(map)));
    }
    
    
    
    
    
    /**
     * A function that assigns a weight to each constant. Used for scoring source assignments according to heuristic preferences in the ACL 2018 experiments.
     * @param g
     * @return 
     */
    @Override
    public double scoreGraph(Pair<SGraph, ApplyModifyGraphAlgebra.Type> g){
        return blobUtils.scoreGraph(g);
    }
    
    
    
    //--------------------------------------------------   top level functions   ------------------------------------------------------------
    
    /**
     * Creates a signature with all relevant constants (including source annotations)
     * for the decomposition automaton.
     * @param graph
     * @param maxCorefs
     * @return
     * @throws ParseException 
     */
    //TODO allow coord to modify!
    // c.f. (c <root> / choose-01 :ARG1 (c2 / concept :quant (explicitanon10 / 100) :ARG1-of (i / innovate-01)) :li (explicitanon11 / 2) :purpose (e / encourage-01 :ARG0 c2 :ARG1 (p / person :ARG1-of (e2 / employ-01)) :ARG2 (a / and :op1 (r / research-01 :ARG0 p) :op2 (d / develop-02 :ARG0 p) :time (o / or :op1 (w / work-01 :ARG0 p) :op2 (t2 / time :poss p :mod (s / spare))))))
    //TODO fix coordination of raising nodes
    //TODO check what happens if we have a graph where a node has two outgoing loc edges! --matthias
    @Override
    public  Signature makeDecompositionSignature(SGraph graph, int maxCorefs) throws ParseException {
        Signature ret = new Signature();
        
        //get all possible sources for each edge
        Map<GraphEdge, Set<String>> edgeSources = new HashMap<>();
        for (GraphEdge e : graph.getGraph().edgeSet()) {
            edgeSources.put(e, new HashSet<>());
        }
        Map<GraphNode, Collection<Map<GraphEdge, String>>> blobSourceAssignments = new HashMap<>();
        for (GraphNode node : graph.getGraph().vertexSet()) {
            Collection<Map<GraphEdge, String>> sourceAssignments = getSourceAssignments(blobUtils.getBlobEdges(graph, node), graph);
            blobSourceAssignments.put(node, sourceAssignments);
            for (Map<GraphEdge, String> map : sourceAssignments) {
                for (GraphEdge e : map.keySet()) {
                    edgeSources.get(e).add(map.get(e));
                }
            }
        }
        
        //make the constant symbols
        Set<String> allConstantSymbols = new HashSet<>();
        for (GraphNode node : graph.getGraph().vertexSet()) {
            Collection<GraphEdge> blobEdges = blobUtils.getBlobEdges(graph, node);
            if (blobUtils.isConjunctionNode(graph, node)) {
                addConstantsForCoordNode(graph, node, blobEdges, maxCorefs, allConstantSymbols);
            } else if (blobUtils.isRaisingNode(graph, node)) {
                addConstantsForRaisingNode(graph, node, blobEdges, maxCorefs, allConstantSymbols);
            } else {
                addConstantsForNormalNode(graph, node, blobEdges, maxCorefs, allConstantSymbols);
            }
        }
        for (String constSymb : allConstantSymbols) {
            ret.addSymbol(constSymb, 0);
        }
        
        // add the operations
        Collection<String> sources = getAllPossibleSources(graph);
        for (String source : sources) {
            ret.addSymbol(ApplyModifyGraphAlgebra.OP_APPLICATION+source, 2);
            ret.addSymbol(ApplyModifyGraphAlgebra.OP_MODIFICATION+source, 2);
        }
        
        //add 'empty node' coreference constants, if applicable
        for (int i = 0; i<maxCorefs; i++) {
            ret.addSymbol(ApplyModifyGraphAlgebra.OP_COREF+i,0);
        }
        
//        System.err.println("nodes: "+graph.getAllNodeNames().size());
//        typeMultCounter.printAllSorted();
//        System.err.println(ret);
        return ret;
    }
    
    
    /**
     * Create a signature with constants for all the given alignments.
     * @param graph
     * @param alignments
     * @param addCoref
     * @return
     * @throws IllegalArgumentException
     * @throws ParseException 
     * @deprecated 
     */
    @Override
    @Deprecated 
    public Signature makeDecompositionSignatureWithAlignments(SGraph graph, List<Alignment> alignments, boolean addCoref) throws IllegalArgumentException, ParseException {
        Signature plainSig = new Signature();
        
        //for each alignment, add all possible constants
        for (Alignment al : alignments) {
            Set<String> consts = getConstantsForAlignment(al, graph, addCoref);
            consts.stream().forEach(c -> plainSig.addSymbol(c, 0));
        }
        Collection<String> sources = getAllPossibleSources(graph);
        
        //add the operations
        for (String s : sources) {
            plainSig.addSymbol(ApplyModifyGraphAlgebra.OP_APPLICATION+s, 2);
            plainSig.addSymbol(ApplyModifyGraphAlgebra.OP_MODIFICATION+s, 2);
        }
        //TODO add coreference symbols here?
        return plainSig;
    }
    
    
    
    /**
     * Runs heuristics to create constants that cover the nodes in TokenAlignment al within the SGraph graph.
     * @param al
     * @param graph
     * @param addCoref
     * @return
     * @throws IllegalArgumentException 
     */
    @Override
    public Set<String> getConstantsForAlignment(Alignment al, SGraph graph, boolean addCoref) throws IllegalArgumentException, ParseException {

        InAndOutNodes inAndOutNodes = new InAndOutNodes(graph, al, blobUtils);

        if (inAndOutNodes.inNodes.size() > 1) {
            throw new IllegalArgumentException("Cannot create a constant for this alignment ("+al.toString()+"): More than one node with edges from outside.");
        }
        //TODO: outNodes could have arbitrary size, but the code is not yet compatible with that.
        if (inAndOutNodes.outNodes.size() > 1) {
            throw new IllegalArgumentException("Cannot create a constant for this alignment ("+al.toString()+"): More than one node with edges to outside.");
        }

        // we know now that there is only one inNode
        GraphNode root = inAndOutNodes.inNodes.iterator().next();
        
        //if there is no node with blob edge pointing out of alignment node cluster, we are done. Otherwise continue, focussing on that one node.
        if (inAndOutNodes.outNodes.isEmpty()) {
            SGraph constGraph = makeConstGraph(al.nodes, graph, root);
            return Collections.singleton(linearizeToAMConstant(constGraph, ApplyModifyGraphAlgebra.Type.EMPTY_TYPE.toString()));
        }
        GraphNode outNode = inAndOutNodes.outNodes.iterator().next();//at this point, there is exactly one. This is the one node in the alignment with blob edges that leave the alignment. For their endpoints, we need to find sources.
        Set<String> ret = new HashSet<>();
        
        Collection<GraphEdge> blobEdges = blobUtils.getBlobEdges(graph, outNode);
        if (blobUtils.isConjunctionNode(graph, outNode)) {
            addConstantsForCoordNode(graph, outNode, al, root, blobEdges, addCoref, ret);
        } else if (blobUtils.isRaisingNode(graph, outNode)) {
            addConstantsForRaisingNode(graph, outNode, al, root, blobEdges, addCoref, ret);
        } else {
            addConstantsForNormalNode(graph, outNode, al, root, blobEdges, addCoref, ret);
        }
        if (addCoref) {
            ret.add(ApplyModifyGraphAlgebra.OP_COREF+al.span.start);
        }
        return ret;
    }
    
    
    
    
    
    // ----------------------   helper functions: making constants for the coordination, control and default cases.  --------------------------------
    
    //TODO the following three cases (coordination, raising and normal nodes) contain some code overlap that should maybe be put in separate functions.
    
    
    // ****   coordination nodes
    /**
     * Interface for decomposition version without alignments
     * @param graph
     * @param node
     * @param ret
     * @param blobEdges
     * @param maxCoref 
     */
    protected void addConstantsForCoordNode(SGraph graph, GraphNode node, Collection<GraphEdge> blobEdges, int maxCoref, Set<String> ret) {
        Set<Integer> corefIDs = new HashSet<>();
        for (int i = 0; i<maxCoref; i++) {
            corefIDs.add(i);
        }
        Set<String> nodes = new HashSet<>();
        nodes.add(node.getName());
        addConstantsForCoordNode(graph, node, nodes, node, blobEdges, corefIDs, ret);
    }
    
    /**
     * Interface for alignment version
     * @param graph
     * @param node
     * @param al
     * @param root
     * @param ret
     * @param blobEdges
     * @param addCoref 
     */
    protected void addConstantsForCoordNode(SGraph graph, GraphNode node, Alignment al, GraphNode root, Collection<GraphEdge> blobEdges, boolean addCoref, Set<String> ret) {
        Set<Integer> corefIDs = new HashSet<>();
        if (addCoref) {
            corefIDs.add(al.span.start);
        }
        addConstantsForCoordNode(graph, node, al.nodes, root, blobEdges, corefIDs, ret);
    }
    
    /**
     * actual function for both versions (with or without alignments)
     * @param graph
     * @param node
     * @param root
     * @param ret
     * @param blobEdges
     * @param corefIDs the ID's allowed for coref sources (use empty set do disallow coreference via indices, as e.g. in the ACL2018 paper)
     */
    protected void addConstantsForCoordNode(SGraph graph, GraphNode node, Set<String> allNodes, GraphNode root, Collection<GraphEdge> blobEdges, Set<Integer> corefIDs, Set<String> ret) {
        for (Map<GraphNode, String> conjTargets : getConjunctionTargets(graph, node)) {
            //conjTargets are the nested targets, e.g. the node w in (node :op1 (v1 :ARG1 w) :op2 (v2 :ARG1 w))
            //now iterate over all subsets of the nested targets (i.e. conjTargetsSubset is a subset of conjTargets). The idea is that maybe only some of the reentrancies are due to coordination, others might be due to coref
            for (Set<GraphNode> conjTargetsSubset : Sets.powerSet(conjTargets.keySet())) {
                // blobTargets is one of the possible maps that assigns sources to the empty nodes in the constant.
                for (Map<GraphNode, String> blobTargets : getBlobTargets(graph, node)) {
                    //first make the basic constant graph (no sources yet)
                    SGraph constGraph = makeConstGraph(allNodes, graph, root);
                    //the nonOpNodes are the nodes without an opX label. TODO: should the check rather use blobUtils.isConjEdgeLabel instead?
                    Set<GraphNode> nonOpNodes = blobTargets.keySet().stream()
                            .filter(n ->!blobTargets.get(n).matches(blobUtils.getCoordRegex())).collect(Collectors.toSet());

                    Map<String,Set<String>> conjTypeStrings = new HashMap<>();//conj source to conj types


                    for (GraphNode recNode : conjTargetsSubset) {
                        Collection<Map<GraphNode, String>> recTargetSet = getTargets(graph, recNode);
                        Set<String> newTypeStrings = new HashSet<>();
                        newTypeStrings.add("()");//this is always an option
                        for (Map<GraphNode, String> recTargets : recTargetSet) {
                            Set<String> newTypeStringsHere = new HashSet<>();
                            newTypeStringsHere.add("(");
                            Set<GraphNode> intersect = Sets.intersection(Sets.union(nonOpNodes, conjTargetsSubset), recTargets.keySet());
                            for (GraphNode recRecNode : intersect) {
                                Set<String> localTypes = Util.appendToAll(newTypeStringsHere, ",", false, s -> !s.endsWith("("));
                                String unifSource = conjTargetsSubset.contains(recRecNode) ? conjTargets.get(recRecNode) : blobTargets.get(recRecNode);
                                localTypes = Util.appendToAll(localTypes, recTargets.get(recRecNode)+"()_UNIFY_"+unifSource, false);
                                newTypeStringsHere.addAll(localTypes);
                            }
                            newTypeStringsHere = Util.appendToAll(newTypeStringsHere, ")", false);
                            newTypeStrings.addAll(newTypeStringsHere);
                        }
                        conjTypeStrings.put(conjTargets.get(recNode), newTypeStrings);
                    }
                    Iterator<String[]> conjTypeTuples;
                    Map<String, Integer> conjSrc2Index = new HashMap<>();
                    if (conjTargetsSubset.isEmpty()) {
                        String[] el = new String[0];
                        conjTypeTuples = Collections.singleton(el).iterator();
                    } else {
                        Set<String>[] conjTypeArray = new Set[conjTargetsSubset.size()];
                        int k = 0;
                        for (GraphNode conjNode : conjTargetsSubset) {
                            conjSrc2Index.put(conjTargets.get(conjNode), k);
                            conjTypeArray[k]=conjTypeStrings.get(conjTargets.get(conjNode));
                            k++;
                        }
                        conjTypeTuples = new TupleIterator<>(conjTypeArray, new String[0]);
                    }


                    while (conjTypeTuples.hasNext()) {
                        String[] conjTypes = conjTypeTuples.next();
                        String conjType = "";
                        for (GraphNode conjNode : conjTargetsSubset) {
                            String conjSource = conjTargets.get(conjNode);
                            if (conjType.length()!=0) {
                                conjType += ",";
                            }
                            conjType += conjSource+"_UNIFY_"+conjSource+conjTypes[conjSrc2Index.get(conjSource)];
                        }
                        Set<String> typeStrings = new HashSet<>();
                        typeStrings.add("(");

                        for (GraphEdge edge : blobEdges) {
                            GraphNode other = GeneralBlobUtils.otherNode(node, edge);

                            if (allNodes.contains(other.getName())) {
                                continue;//do not want to add sources to nodes that are already labelled in the graph fragment for this alignment
                            }
                            
                            String src = blobTargets.get(other);

                            //add source to graph
                            constGraph.addSource(src, other.getName());

                            //add source to type
                            typeStrings = Util.appendToAll(typeStrings, ",", false, s -> s.endsWith(")"));
                            typeStrings = Util.appendToAll(typeStrings, src+"(", false);

                            if (src.matches(blobUtils.coordSourceRegex())) {
                                typeStrings = Util.appendToAll(typeStrings, conjType, false);
                            } else {

                            }
                            //close bracket
                            typeStrings = Util.appendToAll(typeStrings, ")", false);
                        }//finish type and graph strings, add to signature
                        typeStrings = Util.appendToAll(typeStrings, ")", false);
                        for (String typeString : typeStrings) {
                            //typeString = new ApplyModifyGraphAlgebra.Type(typeString).closure().toString();
                            ret.add(linearizeToAMConstant(constGraph, typeString));
                            //coref: index used is the one from alignment!
                            for (int corefID : corefIDs) {
                                // small note: this does not has as robust error messaging as the rest, because it's too complicated,
                                // and not in use right now anyway -- JG
                                String graphString = constGraph.toIsiAmrStringWithSources();
                                graphString = graphString.replaceFirst("<root>", "<root, COREF"+corefID+">");
                                ret.add(OP_COREFMARKER+corefID+"_"+graphString+GRAPH_TYPE_SEP+typeString);
                            }
                        }
                    }


                }
            }
        }
    }
    
    // ****   raising nodes
    /**
     * Interface for decomposition version without alignments
     * @param graph
     * @param node
     * @param ret
     * @param blobEdges
     * @param maxCoref 
     */
    protected void addConstantsForRaisingNode(SGraph graph, GraphNode node, Collection<GraphEdge> blobEdges, int maxCoref, Set<String> ret) {
        Set<Integer> corefIDs = new HashSet<>();
        for (int i = 0; i<maxCoref; i++) {
            corefIDs.add(i);
        }
        Set<String> nodes = new HashSet<>();
        nodes.add(node.getName());
        addConstantsForRaisingNode(graph, node, nodes, node, blobEdges, corefIDs, ret);
    }
    
    /**
     * Interface for alignment version
     * @param graph
     * @param node
     * @param al
     * @param root
     * @param ret
     * @param blobEdges
     * @param addCoref 
     */
    protected void addConstantsForRaisingNode(SGraph graph, GraphNode node, Alignment al, GraphNode root, Collection<GraphEdge> blobEdges, boolean addCoref, Set<String> ret) {
        Set<Integer> corefIDs = new HashSet<>();
        if (addCoref) {
            corefIDs.add(al.span.start);
        }
        addConstantsForRaisingNode(graph, node, al.nodes, root, blobEdges, corefIDs, ret);
    }
    
    /**
     * actual function for both versions (with or without alignments)
     * @param graph
     * @param node
     * @param root
     * @param ret
     * @param blobEdges
     * @param corefIDs 
     */
    protected void addConstantsForRaisingNode(SGraph graph, GraphNode node, Set<String> allNodes, GraphNode root, Collection<GraphEdge> blobEdges, Set<Integer> corefIDs, Set<String> ret) {
        for (Map<GraphNode, String> blobTargets : getBlobTargets(graph, node)) {
            SGraph constGraph = makeConstGraph(allNodes, graph, root);
            Set<String> typeStrings = new HashSet<>();
            typeStrings.add("(");
            for (GraphEdge edge : blobEdges) {
                GraphNode other = GeneralBlobUtils.otherNode(node, edge);
                if (allNodes.contains(other.getName())) {
                    continue;//do not want to add sources to nodes that are already labelled in the graph fragment for this alignment
                }
                String src = blobTargets.get(other);

                //add source to graph
                constGraph.addSource(src, other.getName());

                //add source to type
                typeStrings = Util.appendToAll(typeStrings, ",", false, s -> s.endsWith(")"));
                typeStrings = Util.appendToAll(typeStrings, src+"(", false);



                //intersection of other's and node's targets
                Collection<Map<GraphNode, String>> recTargetsSet = getTargets(graph, other);
                Set<String> newTypeStrings = new HashSet();
                for (Map<GraphNode, String> recTargets : recTargetsSet) {
                    Set<String> newTypeStringsHere = new HashSet(typeStrings);
                    for (Entry<GraphNode, String> recEntry : recTargets.entrySet()) {
                        if ((blobTargets.get(other).equals(OBJ) || blobTargets.get(other).equals(OBJ+"2"))
                                && recEntry.getValue().equals(SUBJ) && !blobTargets.keySet().contains(recEntry.getKey())) {
                            //last condition: do not want to do regular triangles with modality
                            Set<String> localTypes = Util.appendToAll(typeStrings, ",", false, s -> !s.endsWith("("));
                            newTypeStringsHere.addAll(Util.appendToAll(localTypes, SUBJ+"()_UNIFY_"+SUBJ, false));
                        }
                    }

                    Set<GraphNode> intersect = Sets.intersection(blobTargets.keySet(), recTargets.keySet());
                    for (GraphNode recNode : intersect) {
                        Set<String> localTypes = Util.appendToAll(newTypeStringsHere, ",", false, s -> !s.endsWith("("));
                        localTypes = Util.appendToAll(localTypes, recTargets.get(recNode)+"()_UNIFY_"+blobTargets.get(recNode), false);
                        newTypeStringsHere.addAll(localTypes);
                    }
                    newTypeStrings.addAll(newTypeStringsHere);
                }

                typeStrings.addAll(newTypeStrings);
                //close bracket
                typeStrings = Util.appendToAll(typeStrings, ")", false);
            }
            //typeStrings = Util.appendToAll(typeStrings, ",s()", false, s -> s.contains("s()_UNIFY_s"));//always add comma if this condition holds
            //finish type and graph strings, add to signature
            typeStrings = Util.appendToAll(typeStrings, ")", false);
            for (String typeString : typeStrings) {
                //typeString = new ApplyModifyGraphAlgebra.Type(typeString).closure().toString();
                ret.add(linearizeToAMConstant(constGraph, typeString));
                //coref: index used is the one from alignment!
                for (int corefID : corefIDs) {
                    // small note: this does not has as robust error messaging as the rest, because it's too complicated,
                    // and not in use right now anyway -- JG
                    String graphString = constGraph.toIsiAmrStringWithSources();
                    graphString = graphString.replaceFirst("<root>", "<root, COREF"+corefID+">");
                    ret.add(OP_COREFMARKER+corefID+"_"+graphString+GRAPH_TYPE_SEP+typeString);
                }
            }

        }
    }
    // ****   normal/standard nodes
    /**
     * Interface for decomposition version without alignments
     * @param graph
     * @param node
     * @param ret
     * @param blobEdges
     * @param maxCoref 
     */
    protected void addConstantsForNormalNode(SGraph graph, GraphNode node, Collection<GraphEdge> blobEdges, int maxCoref, Set<String> ret) {
        Set<Integer> corefIDs = new HashSet<>();
        for (int i = 0; i<maxCoref; i++) {
            corefIDs.add(i);
        }
        Set<String> nodes = new HashSet<>();
        nodes.add(node.getName());
        addConstantsForNormalNode(graph, node, nodes, node, blobEdges, corefIDs, ret);
    }
    
    /**
     * Interface for alignment version
     * @param graph
     * @param node
     * @param al
     * @param root
     * @param ret
     * @param blobEdges
     * @param addCoref 
     */
    protected void addConstantsForNormalNode(SGraph graph, GraphNode node, Alignment al, GraphNode root, Collection<GraphEdge> blobEdges, boolean addCoref, Set<String> ret) {
        Set<Integer> corefIDs = new HashSet<>();
        if (addCoref) {
            corefIDs.add(al.span.start);
        }
        addConstantsForNormalNode(graph, node, al.nodes, root, blobEdges, corefIDs, ret);
    }
    
    /**
     * actual function for both versions (with or without alignments)
     * @param graph
     * @param node
     * @param root
     * @param ret
     * @param blobEdges
     * @param corefIDs 
     */
    protected void addConstantsForNormalNode(SGraph graph, GraphNode node, Set<String> allNodes, GraphNode root, Collection<GraphEdge> blobEdges, Set<Integer> corefIDs, Set<String> ret) {
        //iterate over all source assignments
        for (Map<GraphNode, String> blobTargets : getBlobTargets(graph, node)) {
            //start with constant graph
            SGraph constGraph = makeConstGraph(allNodes, graph, root);
            
            //find source annotations, i.e. build the constant's type. There are several possibilities, so we build a set of possible types.
            Set<String> typeStrings = new HashSet<>();
            typeStrings.add("(");
            for (GraphEdge edge : blobEdges) {
                GraphNode other = GeneralBlobUtils.otherNode(node, edge);
                if (allNodes.contains(other.getName())) {
                    continue;//do not want to add sources to nodes that are already labelled in the graph fragment for this alignment
                }

                String src = blobTargets.get(other);

                //add source to graph
                constGraph.addSource(src, other.getName());

                //add source to type
                typeStrings = Util.appendToAll(typeStrings, ",", false, s -> s.endsWith(")"));
                typeStrings = Util.appendToAll(typeStrings, src+"(", false);

                //intersection of other's and node's targets
                Collection<Map<GraphNode, String>> recTargetsSet = getTargets(graph, other);
                Set<String> newTypeStrings = new HashSet();
                for (Map<GraphNode, String> recTargets : recTargetsSet) {
                    Set<GraphNode> intersect = Sets.intersection(blobTargets.keySet(), recTargets.keySet());
                    Set<String> newTypeStringsHere = new HashSet(typeStrings);
                    for (GraphNode recNode : intersect) {
                        Set<String> localTypes = Util.appendToAll(newTypeStringsHere, ",", false, s -> !s.endsWith("("));
                        localTypes = Util.appendToAll(localTypes, recTargets.get(recNode)+"()_UNIFY_"+blobTargets.get(recNode), false);
                        newTypeStringsHere.addAll(localTypes);
                    }
                    newTypeStrings.addAll(newTypeStringsHere);
                }
                typeStrings.addAll(newTypeStrings);
                //close bracket
                typeStrings = Util.appendToAll(typeStrings, ")", false);
            }
            //finish type and graph strings, add to returned set
            typeStrings = Util.appendToAll(typeStrings, ")", false);
            for (String typeString : typeStrings) {
                //typeString = new ApplyModifyGraphAlgebra.Type(typeString).closure().toString();
                ret.add(linearizeToAMConstant(constGraph, typeString));
                //coref: index used is the one from alignment!
                for (int corefID : corefIDs) {
                    // small note: this does not has as robust error messaging as the rest, because it's too complicated,
                    // and not in use right now anyway -- JG
                    String graphString = constGraph.toIsiAmrStringWithSources();
                    graphString = graphString.replaceFirst("<root>", "<root, COREF"+corefID+">");
                    ret.add(OP_COREFMARKER+corefID+"_"+graphString+GRAPH_TYPE_SEP+typeString);
                }
            }

        }
    }
    
    
    //----------------------------------------   helpers   ----------------------------------------------------------
    
    protected SGraph makeConstGraph(Set<String> nodes, SGraph graph, GraphNode root) {
        SGraph constGraph = new SGraph();
        for (String nn : nodes) {
            GraphNode node = graph.getNode(nn);
            constGraph.addNode(nn, node.getLabel());
        }
        for (String nn : nodes) {
            GraphNode node = graph.getNode(nn);
            for (GraphEdge e : blobUtils.getBlobEdges(graph, node)) {
                GraphNode other = GeneralBlobUtils.otherNode(node, e);
                if (!nodes.contains(other.getName())) {
                    constGraph.addNode(other.getName(), null);
                }
                constGraph.addEdge(constGraph.getNode(e.getSource().getName()), constGraph.getNode(e.getTarget().getName()), e.getLabel());
            }
        }
        constGraph.addSource("root", root.getName());
        return constGraph;
    }
    
    
    protected String linearizeToAMConstant(SGraph constantGraph, String typeString) {
        try {
            return constantGraph.toIsiAmrStringWithSources()+GRAPH_TYPE_SEP+typeString;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Could not linearize graph: "+constantGraph.toString()+"\n"+ex.getMessage());
        }
    }
    
    
    
    
    
    
    
    
    
    
    
    //----------------------------------------------   source assignments based on edges   ------------------------------------------------
    
    @Deprecated
    public Collection<String> getAllPossibleSources(SGraph graph) {
        Set<String> sources = new HashSet<>();
        sources.add(SUBJ);
        sources.add(OBJ);
        sources.add(OBJ+2);
        sources.add(OBJ+3);
        sources.add(OBJ+4);
        sources.add(OBJ+5);
        sources.add(OBJ+6);
        sources.add(OBJ+7);
        sources.add(OBJ+8);
        sources.add(OBJ+9);
        sources.add(DOMAIN);
        sources.add(POSS);
        sources.add(MOD);
        // op and snt sources are theoretically unlimited in AMR, so we need to look at the graph to find all of them.
        for (GraphEdge e : graph.getGraph().edgeSet()) {
            String eSrc = blobUtils.edge2Source(e, graph);
            if (eSrc.startsWith("op") || eSrc.startsWith("snt")) {
                sources.add(eSrc);
                sources.add(eSrc);
            }
        }
        return sources;
    }
    
    protected Collection<Map<GraphEdge, String>> getSourceAssignments(Collection<GraphEdge> blobEdges, SGraph graph) {
        //first get default mapping (called seed here)
        Map<GraphEdge, String> seed = new HashMap<>();
        blobEdges.stream().sorted(Comparator.comparing(GraphEdge::getLabel)).forEach(e -> { //for all blobEdges sorted alphabetically. TODO: allow custom comparator, that may also take alignments into account
            String sourceName = blobUtils.edge2Source(e, graph);
            if (seed.containsValue(sourceName)){ //we may have the situation where the same edge label occures twice (in DM), so keep counting until we found a source name that is ok.
                int counter = 2;
                while (seed.containsValue(sourceName+counter)){
                    counter++;
                }
                seed.put(e,sourceName+counter);
            } else {
                seed.put(e,sourceName );
            }
        });
        
        //now apply all remappings that are allowed with multiplicity
        Set<Map<GraphEdge, String>> seedSet = new HashSet();
        seedSet.add(seed);
        Set<Map<GraphEdge, String>> multClosure = closureUnderMult(seedSet);
        
        //now apply remappings that are allowed only once
        Set<Map<GraphEdge, String>> ret = new HashSet();
        for (Map<GraphEdge, String> map : multClosure) {
            if (allowOriginalSources) {
                ret.add(map);
            }
            for (Function<Map<GraphEdge, String>,Collection<Map<GraphEdge, String>>> f : lexiconSourceRemappingsOne) {
                ret.addAll(f.apply(map));
            }
        }
        //again apply all remappings that are allowed with multiplicity
        ret = closureUnderMult(ret);
        
        //return all maps that don't have duplicates
        return ret.stream().filter(map -> !hasDuplicates(map)).collect(Collectors.toList());
    }
    
    
    
    /**
     * recursively applies all functions in lexiconSourceRemappingsMulti, keeping both original and new maps
     * @param seedSet
     * @return 
     */
    private Set<Map<GraphEdge, String>> closureUnderMult(Set<Map<GraphEdge, String>> seedSet) {
        Queue<Map<GraphEdge, String>> agenda = new LinkedList<>(seedSet);
        Set<Map<GraphEdge, String>> seen = new HashSet(seedSet);
        while (!agenda.isEmpty()) {
            Map<GraphEdge, String> map = agenda.poll();
            for (Function<Map<GraphEdge, String>,Collection<Map<GraphEdge, String>>> f : lexiconSourceRemappingsMulti) {
                Collection<Map<GraphEdge, String>> newMaps = f.apply(map);
                for (Map<GraphEdge, String> newMap : newMaps) {
                    if (!seen.contains(newMap)) {
                        agenda.add(newMap);
                    }
                }
                seen.addAll(newMaps);
            }
        }
        return seen;
    }
    
    /**
     * checks whether the map assigns the same source to multiple edges.
     * @param edge2sources
     * @return 
     */
    private boolean hasDuplicates(Map<GraphEdge, String> edge2sources) {
        return edge2sources.keySet().size() != new HashSet(edge2sources.values()).size();
    }
    
    private Collection<Map<GraphEdge, String>> passivize(Map<GraphEdge, String> map, int objNr) {
        String objSrc;
        List<Map<GraphEdge, String>> ret = new ArrayList<>();
        if (objNr == 1) {
            objSrc = OBJ;
        } else if (objNr>1) {
            objSrc = OBJ+objNr;
        } else {
            return Collections.EMPTY_LIST;
        }
        for (Entry<GraphEdge, String> entryO : map.entrySet()) {
            if (entryO.getValue().equals(objSrc)) {
                boolean foundS = false;
                for (Entry<GraphEdge, String> entryS : map.entrySet()) {
                    if (entryS.getValue().equals(SUBJ)) {
                        foundS = true;
                        Map<GraphEdge, String> newMap = new HashMap(map);
                        newMap.put(entryO.getKey(), SUBJ);
                        newMap.put(entryS.getKey(), objSrc);
                        ret.add(newMap);
                    }
                }
                if (!foundS) {
                    Map<GraphEdge, String> newMap = new HashMap(map);
                    newMap.put(entryO.getKey(), SUBJ);
                    ret.add(newMap);
                }
            }
        }
        return ret;
    }
    
    private Collection<Map<GraphEdge, String>> promoteObj(Map<GraphEdge, String> map, int objNr) {
        List<Map<GraphEdge, String>> ret = new ArrayList<>();
        String objSrc = OBJ+objNr;
        String smallerSrc;
        if (objNr == 2) {
            smallerSrc = OBJ;
        } else if (objNr>2) {
            smallerSrc = OBJ+(objNr-1);
        } else {
            return Collections.EMPTY_LIST;
        }
        for (Entry<GraphEdge, String> entrySmaller : map.entrySet()) {
            if (entrySmaller.getValue().equals(smallerSrc)) {
                return Collections.EMPTY_LIST;
            }
        }
        for (Entry<GraphEdge, String> entryO : map.entrySet()) {
            if (entryO.getValue().equals(objSrc)) {
                Map<GraphEdge, String> newMap = new HashMap(map);
                newMap.put(entryO.getKey(), smallerSrc);
                ret.add(newMap);
            }
        }
        return ret;
    }
    
    /**
     * Maximally promotes all objects.
     *
     * @param map
     * @return the same map with all objects promoted as high as possible while
     * maintaining the same order
     */
    private Map<GraphEdge, String> promoteObjMax(Map<GraphEdge, String> map) {
        Map<GraphEdge, String> ret = new HashMap<>();
        List<Pair<String, GraphEdge>> objs = new ArrayList<>();
        List<Pair<String, GraphEdge>> objsChanged = new ArrayList<>();

        // get all the objects. We put the value first because we want to sort alphabetically by source name
        for (Entry<GraphEdge, String> entryO : map.entrySet()) {
            if (entryO.getValue().matches(OBJ+"[0-9]*")) {
                objs.add(new Pair<String, GraphEdge>(entryO.getValue(), entryO.getKey()));
            } else {
                // non-objects can go straight into the retern map
                ret.put(entryO.getKey(), entryO.getValue());
            }

        }
        // sort objects alphabetically, which should make obj, obj1, obj2, etc
        objs.sort((Pair<String, GraphEdge> o1, Pair<String, GraphEdge> o2) -> 
                String.CASE_INSENSITIVE_ORDER.compare(o1.left, o2.left));

        // obj1 is just called object. Add it if we have any objects at all
        if (objs.size() > 0) {
            objsChanged.add(new Pair<>(OBJ, objs.get(0).right));
        }

        // for any additional objects, they should be named by their index in the list plus one 
        // (eg the next object should be obj2, and it's at index 1.)
        for (int i = 1; i < objs.size(); i++) {

            objsChanged.add(new Pair<>(OBJ + (Integer.toString(i + 1)), objs.get(i).right));

        }

        // put the newly named objects back
        for (Pair<String, GraphEdge> pair : objsChanged) {
            String string = pair.left;
            GraphEdge edge = pair.right;

            ret.put(edge, string);

        }

        return ret;
    }
    
    /**
     * Maximal object promotion and passivisation
     * @param typedGraph: pair of SGraph and its type. The type is never used, this is for forward compatability with the new am-tools
     * @return 
     */
    public static double scoreGraphPassiveSpecial(Pair<SGraph, ApplyModifyGraphAlgebra.Type> typedGraph) {
        SGraph graph = typedGraph.getLeft();
        double ret = 1.0;
        for (String s : graph.getAllSources()) {
            if (s.matches(OBJ+"[0-9]+")) {
                double oNr = Integer.parseInt(s.substring(1));
                ret /= oNr;
            }
            if (s.equals(SUBJ)) {
                GraphNode n = graph.getNode(graph.getNodeForSource(s));
                Set<GraphEdge> edges = graph.getGraph().edgesOf(n);
                if (!edges.isEmpty()) {                    
                    if (edges.size() > 1) {
                        System.err.println("***WARNING*** more than one edge at node "+n.getName());
                        System.err.println(edges);
                    }
                    GraphEdge e = edges.iterator().next();
                    if (e.getLabel().equals("ARG0")) {
                        ret *= 2.0;
                    } else if (e.getLabel().equals("ARG1")) {
                        ret *= 1.5;
                    }
                } else {
                    System.err.println("***WARNING*** no edges at node "+n.getName());
                }
            }
        }
        return ret;
    }
     
    
    
    
    
    
    
    
    
    
    
    
    
    //--------------------------------------------------   get targets   ------------------------------------------------
    
    /**
     * Essentially returns the blob-targets, except for conjunction/coordination
     * and raising nodes, where the respective nested targets are added, and the
     * intermediate nodes are removed. E.g. if node is u, and we have
     * (u :op1 (v1 :ARG1 w) :op2 (v2 :ARG1 w)), then w is added and v1, v2 are
     * removed (i.e. just the set {w} is returned).
     * @param graph
     * @param node the input node of which to get the targets.
     * @return 
     */
    protected Collection<Map<GraphNode, String>> getTargets(SGraph graph, GraphNode node) {
        Collection<Map<GraphNode, String>> blob = getBlobTargets(graph, node);
        if (blobUtils.isConjunctionNode(graph, node)) {
            Collection<Map<GraphNode, String>> conj = getConjunctionTargets(graph, node);
            Collection<Map<GraphNode, String>> ret = new HashSet<>();
            for (Map<GraphNode, String> blobMap : blob) {
                Map<GraphNode, String> filteredBlobMap = new HashMap<>();
                for (GraphNode n : blobMap.keySet()) {
                    GraphEdge blobEdge = graph.getGraph().getEdge(node, n);
                    if (blobEdge == null || !blobUtils.isConjEdgeLabel(blobEdge.getLabel())) {
                        filteredBlobMap.put(n, blobMap.get(n));
                    }
                }
                blobMap = filteredBlobMap;
                for (Map<GraphNode, String> conjMap : conj) {
                    Map<GraphNode, String> retHere = new HashMap<>(blobMap);
                    boolean hitForbidden = false;
                    for (GraphNode n : conjMap.keySet()) {
                        String s = conjMap.get(n);
                        if (blobMap.values().contains(s)) {
                            hitForbidden = true;
                            break;
                        } else {
                            retHere.put(n, s);//will override blob-targets with conjunction targets. I think this is the wanted behaviour for now -- JG
                        }
                    }
                    if (!hitForbidden) {
                        ret.add(retHere);
                    }
                }
            }
            return ret;
        } else if (blobUtils.isRaisingNode(graph, node)) {
            GraphEdge argEdge = blobUtils.getArgEdges(node, graph).iterator().next();//know we have exactly one if node is raising node
            Collection<Map<GraphNode, String>> ret = new HashSet<>();
            for (Map<GraphNode, String> blobMap : blob) {
//                System.err.println(blobMap);
//                System.err.println(argEdge.getTarget());
//                System.err.println(argEdge);
                if (blobMap.get(argEdge.getTarget()).equals(OBJ) || blobMap.get(argEdge.getTarget()).equals(OBJ+"2")) {
                    for (GraphNode raisedSubj : getRaisingTargets(graph, node)) {
                        if (!blobMap.keySet().contains(raisedSubj)) {
                            Map<GraphNode, String> newMap = new HashMap(blobMap);
                            newMap.put(raisedSubj, SUBJ);
                            ret.add(newMap);
                        }
                    }
                } else {
                    ret.add(blobMap);
                }
            }
            return ret;
        } else {
            return blob;
        }
    }
    
    /**
     * If node has an ARG1 or ARG2 edge (to say a node v) and no other ARGx edges, this returns all the targets
     * of v that are assigned an S or O_i source.
     * @param graph
     * @param node
     * @return 
     */
    protected Collection<GraphNode> getRaisingTargets(SGraph graph, GraphNode node) {
        Set<GraphNode> ret = new HashSet<>();
        Set<GraphEdge> argEdges = blobUtils.getArgEdges(node, graph);
        if (argEdges.size() == 1 &&
                (argEdges.iterator().next().getLabel().equals("ARG1") || argEdges.iterator().next().getLabel().equals("ARG2"))) {
            GraphNode other = GeneralBlobUtils.otherNode(node, argEdges.iterator().next());
            for (Map<GraphNode, String> recTargets : getTargets(graph, other)) {
                for (Entry<GraphNode, String> entry : recTargets.entrySet()) {
                    if (entry.getValue().equals("s") || entry.getValue().matches("o[0-9]*")) {
                        ret.add(entry.getKey());
                    }
                }
            }
        }
        return ret;
    }
    
    /**
     * Returns maps from the common targets of the nodes coordinated by coordNode,
     * to source names. I.e. if
     * coordNode is u, and we have (u :op1 (v1 :ARG1 w) :op2 (v2 :ARG1 w)), then
     * both v1 and v2 can assign both O and S (through passive) to w. Thus we get
     * two maps, one mapping w to O, and one mapping w to S. 
     * @param graph
     * @param coordNode
     * @return 
     */
    private Collection<Map<GraphNode, String>> getConjunctionTargets(SGraph graph, GraphNode coordNode) {
        if (blobUtils.isConjunctionNode(graph, coordNode)) {
            Collection<Map<GraphNode, String>> ret = new HashSet<>();
            Set<GraphNode> jointTargets = new HashSet();
            jointTargets.addAll(graph.getGraph().vertexSet());//add all first, remove wrong nodes later
            List<GraphNode> opTargets = new ArrayList<>();
            for (GraphEdge edge : graph.getGraph().outgoingEdgesOf(coordNode)) {
                if (blobUtils.isConjEdgeLabel(edge.getLabel())) {
                    GraphNode other = GeneralBlobUtils.otherNode(coordNode, edge);
                    opTargets.add(other);
                    Collection<Map<GraphNode, String>> otherTargets = getTargets(graph, other);
                    Set<GraphNode> targetsHere = new HashSet<>();
                    for (Map<GraphNode, String> otMap : otherTargets) {
                        targetsHere.addAll(otMap.keySet());
                    }
                    jointTargets.removeIf(lambdaNode -> !targetsHere.contains(lambdaNode));
                }
            }
            Collection<Map<GraphNode, String>>[] iterable = opTargets.stream().map(opTgt -> getTargets(graph, opTgt)).collect(Collectors.toList())
                    .toArray(new Collection[0]);
            TupleIterator<Map<GraphNode, String>> tupleIt = new TupleIterator<>(iterable, new Map[iterable.length]);
            
            //get maxDomain (maybe unnecessary)
            Set<Set<GraphNode>> maxDomains = new HashSet<>();
            int maxDomainSize = 0;
            while (tupleIt.hasNext()) {
                Map<GraphNode, String>[] targets = tupleIt.next();
                Set<GraphNode> domainHere = new HashSet<>();
                for (GraphNode jt : jointTargets) {
                    boolean consensus = true;
                    String consensusString = targets[0].get(jt);
                    for (Map<GraphNode, String> n2s : targets) {
                        if (consensusString == null || !consensusString.equals(n2s.get(jt))) {
                            consensus = false;
                            break;
                        }
                    }
                    if (consensus) {
                        domainHere.add(jt);
                    }
                }
                if (domainHere.size() == maxDomainSize) {
                    maxDomains.add(domainHere);
                } else if (domainHere.size() > maxDomainSize) {
                    maxDomainSize = domainHere.size();
                    maxDomains = new HashSet();
                    maxDomains.add(domainHere);
                }
            }
            Set<GraphNode> maxDomain = graph.getGraph().vertexSet();
            for (Set<GraphNode> dom : maxDomains) {
                maxDomain = Sets.intersection(maxDomain, dom);
            }
            
            tupleIt = new TupleIterator<>(iterable, new Map[iterable.length]);
            while (tupleIt.hasNext()) {
                Map<GraphNode, String>[] targets = tupleIt.next();
                Set<GraphNode> domainHere = new HashSet<>();
                for (GraphNode jt : jointTargets) {
                    boolean consensus = true;
                    String consensusString = targets[0].get(jt);
                    for (Map<GraphNode, String> n2s : targets) {
                        if (consensusString == null || !consensusString.equals(n2s.get(jt))) {
                            consensus = false;
                            break;
                        }
                    }
                    if (consensus) {
                        domainHere.add(jt);
                    }
                }
                if (domainHere.containsAll(maxDomain)) {
                    Map<GraphNode, String> retHere = new HashMap<>();
                    for (GraphNode jt : maxDomain) {
                        retHere.put(jt, targets[0].get(jt));//which target doesnt matter, because of consensus
                    }
                    ret.add(retHere);
                }
            }
            
            return ret;
            
            
        } else {
            return new HashSet<>();
        }
    }
    
    /**
     * Returns all possible blob-target-maps (each maps each blob target to a source).
     * @param graph
     * @param node
     * @return 
     */
    protected Collection<Map<GraphNode, String>> getBlobTargets(SGraph graph, GraphNode node) {
        Collection<Map<GraphNode, String>> ret = new HashSet<>();
        for (Map<GraphEdge, String> map : getSourceAssignments(blobUtils.getBlobEdges(graph, node), graph)) {
            Map<GraphNode, String> retHere = new HashMap<>();
            for (GraphEdge edge : map.keySet()) {
                retHere.put(GeneralBlobUtils.otherNode(node, edge), map.get(edge));
            }
            ret.add(retHere);
        }
        return ret;
    }
    
    
    
    
    
    
    
    
    
    //------------------------------------------------------------   main for testing things   ------------------------------------------------------------------
    
    
    public static void main(String[] args) throws FileNotFoundException, IOException, CorpusReadingException, ParseException, ParserException, InterruptedException {
        SGraph g = new GraphAlgebra().parseString("(l <root> / love :ARG0 (p / prince) :ARG1 (r/rose))");
        AMRSignatureBuilder amrSigBuilder = new AMRSignatureBuilder();
        ApplyModifyGraphAlgebra alg = new ApplyModifyGraphAlgebra(amrSigBuilder.makeDecompositionSignature(g, 0));
        System.err.println(alg.getSignature());
        TreeAutomaton decomp = new AMDecompositionAutomaton(alg,null, g);//getCorefWeights(alLine, graph), graph);
        decomp.processAllRulesBottomUp(null, 60000);
        System.err.println(decomp);
        System.err.println(decomp.countTrees());
        
        
//        Signature sig = new Signature();
//        InterpretedTreeAutomaton irtg = new InterpretedTreeAutomaton(new ConcreteTreeAutomaton<>(sig));
//        GraphAlgebra graphAlg = new GraphAlgebra();
//        irtg.addInterpretation("graph", new Interpretation(graphAlg, new Homomorphism(sig, graphAlg.getSignature())));
//        
//        Corpus corp = Corpus.readCorpus(new FileReader("../../experimentData/Corpora/iwcsTest.corpus"), irtg);
//        
//        int i = 0;
//        for (Instance inst : corp) {
//            System.err.println(i);
//            i++;
//            SGraph graph = (SGraph)inst.getInputObjects().get("graph");
//            Signature decompSig = makeDecompositionSignature(graph, 0);
//            System.err.println(decompSig);
//            ApplyModifyGraphAlgebra alg = new ApplyModifyGraphAlgebra(decompSig);
//            TreeAutomaton decomp = new AMDecompositionAutomaton(alg, null, graph);
//            decomp.makeAllRulesExplicit();
////            long time = System.currentTimeMillis();
////            try {
////                decomp.makeExplicitBottomUp(auto -> (!((TreeAutomaton)auto).getFinalStates().isEmpty() || System.currentTimeMillis()-time>1000));
////            } catch (InterruptedException ex) {
////                
////            }
//            
//            
//            try (Writer w = new FileWriter("../../experimentData/decompositionAutomata/iwcsTest/"+i+".auto")) {
//                w.write(decomp.toString());
//            }
//            System.err.println(decomp.viterbi() != null);
//        }
    }


    public static class InAndOutNodes {
        public final Set<GraphNode> inNodes;
        public final Set<GraphNode> outNodes;

        /**
         * For a given alignment, computes "inNodes", the set of all nodes that need to be roots in the constant
         * corresponding to the alignment, i.e. the global root of the whole graph and all nodes with incident edges
         * from outside blobs (edges entering this constant). Also computes the
         * "outNodes", the set of all nodes that have edges leaving this constant. If "inNodes" is empty, then , an
         * arbitrary lexical node (or as a backup if no lexical node exists, an arbitrary node) is added to the set.
         * So inNodes is never empty.
         * @param graph The whole graph
         * @param al The alignment
         * @param blobUtils
         */
        public InAndOutNodes(SGraph graph, Alignment al, AMRBlobUtils blobUtils) {
            outNodes = new HashSet<>();//contains all nodes that have edges leaving this constant.
            inNodes = new HashSet<>();//contains all nodes that need to be roots, i.e. the global root and all nodes
            // with incident edges from outside blobs (edges entering this constant).
            // None of the above takes edge direction into account, it is only about which blob an edge belongs to.
            String globalRootNN = graph.getNodeForSource(ApplyModifyGraphAlgebra.ROOT_SOURCE_NAME);
            if (globalRootNN != null && al.nodes.contains(globalRootNN)) {
                    inNodes.add(graph.getNode(globalRootNN));
            }
            for (String nn : al.nodes) {
                GraphNode node = graph.getNode(nn);
                for (GraphEdge e : graph.getGraph().edgesOf(node)) {
                    if (!al.nodes.contains(e.getSource().getName()) || !al.nodes.contains(e.getTarget().getName())) {
                        if (blobUtils.isBlobEdge(node, e)) {
                            outNodes.add(node);
                        } else {
                            inNodes.add(node);
                        }
                    }
                }
            }
            if (inNodes.isEmpty()) {
                //take arbitrary node
                //but prefer the lexical node - ml
                if (!al.lexNodes.isEmpty()){
                    inNodes.add(graph.getNode(al.lexNodes.iterator().next()));
                } else {
                    inNodes.add(graph.getNode(al.nodes.iterator().next()));
                }
            }
        }
    }
    
    
}
