package de.saar.coli.amtools.decomposition.formalisms.decomposition_packages;

import edu.stanford.nlp.ling.CoreLabel;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SizeFixerUCCA {

    private  List<String> refinedPos = new ArrayList<>();
    private List<String> refinedLemmas = new ArrayList<>();
    private List<String> refinedNes = new ArrayList<>();


    private List<List<String>> bundle = new ArrayList<>();

    private List<String> mappedPosTags;
    private List<CoreLabel> tokens;
    private List<String> mappedLemmas;
    private List<String> sentWords;



    public SizeFixerUCCA(List<String> mappedPosTags, List<CoreLabel> tokens, List<String> mappedLemmas, List<String> sentWords) {

        //this.mappedPosTags = mappedPosTags;
        //this.mappedLemmas = mappedLemmas;
        this.sentWords = sentWords;
        this.tokens = tokens;
    }


    public List<List<String>> adjust(List<String> mappedPosTags, List<String> mappedLemmas, List<String> neTags) {



        Iterator<CoreLabel> tokenIterator = tokens.iterator();
        Iterator<String> lemmaIterator = mappedLemmas.iterator();
        Iterator<String> posIterator = mappedPosTags.iterator();
        Iterator<String> sentIterator = sentWords.iterator();
        Iterator<String> neIterator = neTags.iterator();



        //System.out.println("__________________________");

        //s is the potential multi-word token that we need to find. Everything is should be set according to this
        //in terms of length
        String s = sentIterator.next();
        //pos and lemma are obtained from tokens, which doesn't keep multi-word tokens together
        String pos = posIterator.next();
        String lemma = lemmaIterator.next();
        //tokens is the token list
        CoreLabel token = tokenIterator.next();
        String ne = neIterator.next();
        //System.out.println(mappedPosTags);

        int i = 0;
        int sentSize = sentWords.size();



        while(i < sentSize){
            //System.out.println(s + " contains " + token);
            if (s.toLowerCase().equals(token.originalText().toLowerCase())||s.toLowerCase().contains(token.toString().toLowerCase()) && s.contains(" ")){
                //System.out.println("True");

                refinedLemmas.add(lemma);
                refinedPos.add(pos);
                refinedNes.add(ne);

                //System.out.println(i);

                if (sentIterator.hasNext()) {
                    s = sentIterator.next();
                }
                i++;

            }

            else{
                System.out.println("False");
                pos = posIterator.next();
                lemma = lemmaIterator.next();
                token = tokenIterator.next();

                ne = neIterator.next();
            }

            //System.out.println(s);
            //System.out.println(token);
            //System.out.println(i + " of " + sentSize);
            //System.out.println(refinedLemmas);
            //System.out.println("__________________________________________");



        }

        bundle.add(refinedLemmas);
        bundle.add(refinedPos);
        bundle.add(refinedNes);
        //System.out.println(refinedLemmas);
        return bundle;

    }

}

