package de.saar.coli.amrtagging.formalisms.cogs;

import de.saar.coli.amrtagging.Alignment;
import de.saar.coli.amrtagging.MRInstance;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.saar.coli.amrtagging.formalisms.cogs.COGSLogicalForm.Argument;
import de.saar.coli.amrtagging.formalisms.cogs.COGSLogicalForm.Term;
import org.apache.commons.lang.NotImplementedException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Converts <code>COGSLogicalForm</code> to a <code>SGraph</code><br>
 *
 * Version 1: very AMR-like<br>
 * - arguments of a term (x_i, John, a) become nodes<br>
 * - predicate names become edges unless it's a term with only one argument ( boy(x1) ), then it's part of the x_i node
 * - iota: we treat iota as some special term: `* boy ( x _ 1 ) ;` transformed to `the.iota( the, x_1_boy )`<br>
 * - prepositions: the <i>nmod.preposition</i> edge belongs to the noun of the PP (not the modifed noun!)<br>
 * - primitives: treated as graphs with open sources...<br>
 * TODO: missing implementation:
 * - Alignment: is is 0-indexed or 1-indexed? currently assumes 0-indexed. Check what Alignment wants and maybe change..
 * - refactoring this giant method into smaller ones
 * - non-primitives need to have a root node: how to determine which one it is?
 * - lemma for lambda primitive: how to get lemma from the formula?
 * - node names for indices: just the number or better <code>x_i</code> ?
 * - what is a node name (not label) for proper names? need to recover in postprocessing something?
 * TODO: Problems
 * - alignments for determiners and proper names rely on heuristics and hope (see to-do-notes below)
 * - same would hold for prepositions, but the current encoding transforms them to edges (only nodes need alignments)
 * @author piaw (weissenh)
 * Created April 2021
 */
public class LF2GraphConverter {
    public static final String ROOT_SOURCE_STRING = "root";
    public static final String LEMMA_SEPARATOR = "~~";  // "x_1~~boy", "x_4~~want", "x_e~~giggle", "u~~Ava"
    public static final String IOTA_EDGE_LABEL = "iota";
    public static final String IOTA_NODE_LABEL = "the";

    private static MRInstance LambdaToSGraph(COGSLogicalForm logicalForm, List<String> sentenceTokens) {
        assert(logicalForm.getFormulaType() == COGSLogicalForm.AllowedFormulaTypes.LAMBDA);
        assert(sentenceTokens.size() == 1);
        List<Alignment> alignments = new ArrayList<>();
        SGraph graph = new SGraph();
        // ** Graph
        // - node for each lambda variable
        Set<Argument> lambdaargs = logicalForm.getArgumentSet();
        for (Argument arg: lambdaargs) { graph.addNode(arg.getName(), null); }
        // * need to find which node 'aligns' with the lemma:
        //  this becomes the root node (and latter also lemma added to the node label)
        Argument lexarg = logicalForm.getLexicalArgumentForLambda();
        GraphNode lexicalNode = graph.getNode(lexarg.getName());
        String lexnodename = lexicalNode.getName();
        graph.addSource(ROOT_SOURCE_STRING, lexnodename);
        // * for each predicate (if binary) add edge
        //   on the target node of that edge we add a source (=lambda variable!)
        String lemma = null;
        for (Term t: logicalForm.getAllTerms()) {
            String tmp = t.getPredicate().getLemma();
            if (lemma == null) { lemma = tmp;}
            else { assert(lemma.equals(tmp)); }
            if (t.hasTwoArguments()) {
                Argument firstArg = t.getArguments().get(0);
                GraphNode firstNode = graph.getNode(firstArg.getName());
                Argument secondArg = t.getArguments().get(1);
                GraphNode secondNode = graph.getNode(secondArg.getName());
                String label = t.getPredicate().getDelexPredAsString();
                // todo assert(term.pred.lemma == firstArgNode lemma)
                graph.addEdge(firstNode, secondNode, label);
                // we also know that the target Node should contain a source (lambda variable as source!!)
                graph.addSource(secondArg.getName(), secondNode.getName());
            }
        }
        // * add the lemma to the lexical(= root) node
        assert(lemma != null);  // should have seen at least one term
        lexicalNode.setLabel(lexarg.getName()+LEMMA_SEPARATOR+lemma);
        // ** Alignments
        //  only the 'lexical'/'lemma' node (== the root) is aligned.
        //  The rest are unlabeled nodes with sources. There is only one word in the input, so at position 0.
        alignments.add(new Alignment(lexnodename, 0));
        return new MRInstance(sentenceTokens, graph, alignments);
    }

    // todo IMPORTANT missing assignment of root node!
    private static MRInstance IotaToSGraph(COGSLogicalForm logicalForm, List<String> sentenceTokens) {
        assert (logicalForm.getFormulaType() == COGSLogicalForm.AllowedFormulaTypes.IOTA);
        assert (sentenceTokens.size() > 0);
        List<Alignment> alignments = new ArrayList<>();
        SGraph graph = new SGraph();
        // ** Graph
        // * node for each distinct argument (including proper names!)
        for (Argument arg: logicalForm.getArgumentSet()) { graph.addNode(arg.getName(), null); }
        // * iotas:
        //   - node for each iota (node has label "the")
        //   - iota edge (label: "iota")
        //   - label the noun node with the noun lemma
        //   - align the noun node according to the index
        GraphNode nounNode;
        for (Term t: logicalForm.getPrefixTerms()) {  // for each iota term
            // get the 'noun' node
            assert(t.getValency()==1);  // not binary predicate, but unary one
            Argument nounArgument = t.getArguments().get(0);
            assert(nounArgument.isIndex());
            nounNode = graph.getNode(nounArgument.getName());
            // add lemma to the 'noun' node
            assert(nounNode.getLabel() == null);
            nounNode.setLabel(t.getLemma());
            // add alignment for the 'noun' node
            alignments.add(new Alignment(nounNode.getName(), nounArgument.getIndex()));
            // add new determiner node
            GraphNode determinerNode = graph.addAnonymousNode(IOTA_NODE_LABEL);
            // todo for future extensions this heuristic may become a problem:
            /* How to align the determiner node?
             * - the logical form doesn't specify (no index for it!) which token it belongs to
             * - also: there can be more than one definite determiner in a sentence...
             * - our simple heuristic: we align the determiner node to the token *right before* the noun.
             * - IMPORTANT: we can only use this heuristic faithfully because we know that the COGS dataset
             *   doesn't contain any pre-nominal modifiers like adjectives
             * */
            alignments.add(new Alignment(determinerNode.getName(), nounArgument.getIndex()-1));
            // add an iota-edge
            graph.addEdge(determinerNode, nounNode, IOTA_EDGE_LABEL);
        }
        // * add lemma as a label for each node corresponding to the first argument in some term
        // * edge for each term excluding unary
        GraphNode lemmaNode;
        for (Term t: logicalForm.getConjunctionTerms()) {
            // set lemma for the source node
            Argument firstArg = t.getArguments().get(0);
            assert(firstArg.isIndex());
            lemmaNode = graph.getNode(firstArg.getName());
            assert(lemmaNode.getLabel() == null || lemmaNode.getLabel().equals(t.getLemma()));
            // if node doesn't have this lemma label already: add it and also add alignment
            if (lemmaNode.getLabel() == null) {
                lemmaNode.setLabel(t.getLemma());
                // add alignment for the lemma node
                alignments.add(new Alignment(lemmaNode.getName(), firstArg.getIndex()));
            }
            // if there is a second argument, we add an edge:
            if (t.hasTwoArguments()) {
                Argument targetArg = t.getArguments().get(1);
                GraphNode targetNode = graph.getNode(targetArg.getName());
                graph.addEdge(lemmaNode, targetNode, t.getPredicate().getDelexPredAsString());
            }
        }
        // ** Alignment heuristic for proper names:
        /* we just *hope* that each name only appears once in a sentence:
         * Because we search for the first index in the sentence tokens list that equals the Name and that we
         * align accordingly
         * also note how we defined equals() on Argument
         * todo IMPORTANT: here is another alignment heuristic which can become problematic
         */
        // note: worst case quadratic in the length of the sentence (for each proper name we go iterate over the list of tokens)
        GraphNode properNameNode;
        for (Argument argument: logicalForm.getArgumentSet()) {
            if (argument.isProperName()) {
                properNameNode = graph.getNode(argument.getName());
                int token_position = sentenceTokens.indexOf(argument.getName());
                assert(token_position != -1);
                alignments.add(new Alignment(properNameNode.getName(), token_position));
            }
        }
        return new MRInstance(sentenceTokens, graph, alignments);
    }

    public static MRInstance toSGraph(COGSLogicalForm logicalForm, List<String> sentenceTokens) {
        if (sentenceTokens.size() == 0 ) { throw new RuntimeException("Empty sentence not allowed"); }
        switch (logicalForm.getFormulaType()) {
            case LAMBDA:
                return LambdaToSGraph(logicalForm, sentenceTokens);
            case IOTA:
                // todo checking validity of indices: shouldn't be done with assert (public!) but with exception!
                int length = sentenceTokens.size();
                for (Argument arg: logicalForm.getArgumentSet()) {
                    assert !arg.isIndex() || (0 <= arg.getIndex() && arg.getIndex() < length);
                }
                return IotaToSGraph(logicalForm, sentenceTokens);
            case NAME:
                assert(sentenceTokens.size() == 1);
                List<Alignment> alignments = new ArrayList<>();
                SGraph graph = new SGraph();
                Argument propername = logicalForm.getNamePrimitive();
                // ** Graph: add node with the proper name as label and make it the root
                GraphNode node = graph.addAnonymousNode(propername.getName()); // todo what about lemma? needed?
                graph.addSource(ROOT_SOURCE_STRING, node.getName());
                // ** Alignments: align to first and only word in the sentence
                alignments.add(new Alignment(node.getName(), 0));
                return new MRInstance(sentenceTokens, graph, alignments);
            default:
                assert (false);
                return null;
        }
    }

    public static COGSLogicalForm toLogicalForm(MRInstance mr) {
        // reverse of toSGraph
        // what kind of formula? LAMBDA, NAME or IOTA?
        // - split graphs  (uh: primitives, preposition...)
        // - revert iota
        // - add unary predicates (no outgoing edges (modula nmod ones) and not a proper Name? ): either put in conjunction or in prefix?
        // - re-lexicalize edges
        // - sort terms based on indices
        throw new NotImplementedException("Not implemented yet");
    }
}