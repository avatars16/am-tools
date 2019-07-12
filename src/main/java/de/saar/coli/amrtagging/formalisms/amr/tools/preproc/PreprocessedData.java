/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.amr.tools.preproc;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.TaggedWord;
import java.util.List;

/**
 * Data which has optionally been tokenized and POS-tagged.
 * Implementations of this class may choose to either compute
 * the tokens and POS tags themselves, or look them up in a file.<p>
 * 
 * This class assumes that every instance in the corpus has an ID,
 * and will return lists of tokens or tags for a given instance ID.
 * 
 * 
 * @author koller
 */
public interface PreprocessedData {
    /**
     * Returns the POS tags of the sentence, one per token.
     * 
     * @param instanceId
     * @return 
     */
    public List<TaggedWord> getPosTags(String instanceId);
    
    /**
     * Returns the tokens of the sentence. Each token is
     * guaranteed to contain at least the original word,
     * which can be retrieved with {@link CoreLabel#word() }.
     * 
     * @param instanceId
     * @return 
     */
    public List<CoreLabel> getTokens(String instanceId);
    
    /**
     * Sets the tokens for the sentence. Use this method if
     * a tokenization was already available, e.g. from a previous
     * preprocessing step.
     * 
     * @param instanceId
     * @param tokens 
     */
    public void setTokens(String instanceId, List<String> tokens);
    
    /**
     * Sets the untokenized sentence. If {@link #setTokens(java.lang.String, java.util.List) }
     * is called for the same instanceId before or after a call to this
     * method, the more explicit information passed to setTokens
     * takes priority, and the call to this method will have no effect.
     * 
     * @param instanceId
     * @param sentence 
     */
    public void setUntokenizedSentence(String instanceId, String sentence);
}
