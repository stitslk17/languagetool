/* LanguageTool, a natural language style checker 
 * Copyright (C) 2007 Daniel Naber (http://www.danielnaber.de)
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301
 * USA
 */
package org.languagetool.language;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.languagetool.*;
import org.languagetool.chunking.Chunker;
import org.languagetool.chunking.GermanChunker;
import org.languagetool.languagemodel.LanguageModel;
import org.languagetool.rules.*;
import org.languagetool.rules.de.LongSentenceRule;
import org.languagetool.rules.de.SentenceWhitespaceRule;
import org.languagetool.rules.de.*;
import org.languagetool.rules.neuralnetwork.NeuralNetworkRuleCreator;
import org.languagetool.rules.neuralnetwork.Word2VecModel;
import org.languagetool.synthesis.GermanSynthesizer;
import org.languagetool.synthesis.Synthesizer;
import org.languagetool.tagging.Tagger;
import org.languagetool.tagging.de.GermanTagger;
import org.languagetool.tagging.disambiguation.Disambiguator;
import org.languagetool.tagging.disambiguation.rules.de.GermanRuleDisambiguator;
import org.languagetool.tokenizers.*;
import org.languagetool.tokenizers.de.GermanCompoundTokenizer;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Support for German - use the sub classes {@link GermanyGerman}, {@link SwissGerman}, or {@link AustrianGerman}
 * if you need spell checking.
 */
public class German extends Language implements AutoCloseable {

  private static final Language GERMANY_GERMAN = new GermanyGerman();

  private CompoundWordTokenizer compoundTokenizer;
  private GermanCompoundTokenizer strictCompoundTokenizer;
  private LanguageModel languageModel;
  private List<Rule> nnRules;
  private Word2VecModel word2VecModel;

  /**
   * @deprecated use {@link GermanyGerman}, {@link AustrianGerman}, or {@link SwissGerman} instead -
   *  they have rules for spell checking, this class doesn't (deprecated since 3.2)
   */
  @Deprecated
  public German() {
  }
  
  @Override
  public Language getDefaultLanguageVariant() {
    return GERMANY_GERMAN;
  }

  @Override
  public Disambiguator createDefaultDisambiguator() {
    return new GermanRuleDisambiguator();
  }

  @Nullable
  @Override
  public Chunker createDefaultPostDisambiguationChunker() {
    return new GermanChunker();
  }

  @Override
  public String getName() {
    return "German";
  }

  @Override
  public String getShortCode() {
    return "de";
  }

  @Override
  public String[] getCountries() {
    return new String[]{"LU", "LI", "BE"};
  }

  @NotNull
  @Override
  public Tagger createDefaultTagger() {
    return new GermanTagger();
  }

  @Nullable
  @Override
  public Synthesizer createDefaultSynthesizer() {
    return new GermanSynthesizer(this);
  }

  @Override
  public SentenceTokenizer createDefaultSentenceTokenizer() {
    return new SRXSentenceTokenizer(this);
  }

  @Override
  public Contributor[] getMaintainers() {
    return new Contributor[] {
        new Contributor("Jan Schreiber"),
        Contributors.DANIEL_NABER,
    };
  }

  @Override
  public List<Rule> getRelevantRules(ResourceBundle messages, UserConfig userConfig, Language motherTongue, List<Language> altLanguages) throws IOException {
    return Arrays.asList(
            new CommaWhitespaceRule(messages,
                    Example.wrong("Die Partei<marker> ,</marker> die die letzte Wahl gewann."),
                    Example.fixed("Die Partei<marker>,</marker> die die letzte Wahl gewann.")),
            new GenericUnpairedBracketsRule(messages,
                    Arrays.asList("[", "(", "{", "„", "»", "«", "\""),
                    Arrays.asList("]", ")", "}", "“", "«", "»", "\"")),
            new UppercaseSentenceStartRule(messages, this,
                    Example.wrong("Das Haus ist alt. <marker>es</marker> wurde 1950 gebaut."),
                    Example.fixed("Das Haus ist alt. <marker>Es</marker> wurde 1950 gebaut.")),
            new MultipleWhitespaceRule(messages, this),
            // specific to German:
            new SimpleReplaceRule(messages),
            new OldSpellingRule(messages),
            new SentenceWhitespaceRule(messages),
            new GermanDoublePunctuationRule(messages),
            new MissingVerbRule(messages, this),
            new GermanWordRepeatRule(messages, this),
            new GermanWordRepeatBeginningRule(messages, this),
            new GermanWrongWordInContextRule(messages),
            new AgreementRule(messages, this),
            new AgreementRule2(messages, this),
            new CaseRule(messages, this),
            new DashRule(messages),
            new VerbAgreementRule(messages, this),
            new SubjectVerbAgreementRule(messages, this),
            new WordCoherencyRule(messages),
            new SimilarNameRule(messages),
            new WiederVsWiderRule(messages),
            new WhiteSpaceBeforeParagraphEnd(messages, this),
            new WhiteSpaceAtBeginOfParagraph(messages),
            new EmptyLineRule(messages, this),
            new GermanStyleRepeatedWordRule(messages, this, userConfig),
            new CompoundCoherencyRule(messages),
            new LongSentenceRule(messages, userConfig, 35, true, true),
            new LongParagraphRule(messages, this, userConfig),
            new GermanFillerWordsRule(messages, this, userConfig),
            new GermanParagraphRepeatBeginningRule(messages, this),
            new PunctuationMarkAtParagraphEnd(messages, this),
            new DuUpperLowerCaseRule(messages),
            new UnitConversionRule(messages),
            new MissingCommaRelativeClauseRule(messages),
            new MissingCommaRelativeClauseRule(messages, true),
            new GermanReadabilityRule(messages, this, userConfig, true),
            new GermanReadabilityRule(messages, this, userConfig, false),
            new CompoundInfinitivRule(messages, this, userConfig)
    );
  }

  /** @since 3.1 */
  @Override
  public List<Rule> getRelevantLanguageModelRules(ResourceBundle messages, LanguageModel languageModel, UserConfig userConfig) throws IOException {
    return Arrays.asList(
            new UpperCaseNgramRule(messages, languageModel, this),
            new GermanConfusionProbabilityRule(messages, languageModel, this),
            new ProhibitedCompoundRule(messages, languageModel, userConfig)
    );
  }

  /** @since 4.0 */
  @Override
  public List<Rule> getRelevantWord2VecModelRules(ResourceBundle messages, Word2VecModel word2vecModel) throws IOException {
    if (nnRules == null) {
      nnRules = NeuralNetworkRuleCreator.createRules(messages, this, word2vecModel);
    }
    return nnRules;
  }

  /**
   * @since 2.7
   */
  public CompoundWordTokenizer getNonStrictCompoundSplitter() {
    if (compoundTokenizer == null) {
      try {
        GermanCompoundTokenizer tokenizer = new GermanCompoundTokenizer(false);  // there's a spelling mistake in (at least) one part, so strict mode wouldn't split the word
        compoundTokenizer = word -> new ArrayList<>(tokenizer.tokenize(word));
      } catch (IOException e) {
        throw new RuntimeException("Could not set up German compound splitter", e);
      }
    }
    return compoundTokenizer;
  }

  /**
   * @since 2.7
   */
  public GermanCompoundTokenizer getStrictCompoundTokenizer() {
    if (strictCompoundTokenizer == null) {
      try {
        strictCompoundTokenizer = new GermanCompoundTokenizer();
      } catch (IOException e) {
        throw new RuntimeException("Could not set up strict German compound splitter", e);
      }
    }
    return strictCompoundTokenizer;
  }

  @Override
  public synchronized LanguageModel getLanguageModel(File indexDir) throws IOException {
    languageModel = initLanguageModel(indexDir, languageModel);
    return languageModel;
  }

  /** @since 4.0 */
  @Override
  public synchronized Word2VecModel getWord2VecModel(File indexDir) throws IOException {
    if (word2VecModel == null) {
      word2VecModel = new Word2VecModel(indexDir + File.separator + getShortCode());
    }
    return word2VecModel;
  }

  /**
   * Closes the language model, if any. 
   * @since 3.1 
   */
  @Override
  public void close() throws Exception {
    if (languageModel != null) {
      languageModel.close();
    }
  }
  
  /** @since 5.1 */
  @Override
  public String getOpeningDoubleQuote() {
    return "„";
  }

  /** @since 5.1 */
  @Override
  public String getClosingDoubleQuote() {
    return "“";
  }
  
  /** @since 5.1 */
  @Override
  public String getOpeningSingleQuote() {
    return "‚";
  }

  /** @since 5.1 */
  @Override
  public String getClosingSingleQuote() {
    return "‘";
  }
  
  /** @since 5.1 */
  @Override
  public boolean isAdvancedTypographyEnabled() {
    return true;
  }
  
  @Override
  public String toAdvancedTypography (String input) {
    String output = super.toAdvancedTypography(input);
    
    //non-breaking space
    output = output.replaceAll("\\b([a-zA-Z]\\.)([a-zA-Z]\\.)", "$1\u00a0$2");
    return output;
  }
  


  
  @Override
  public LanguageMaintainedState getMaintainedState() {
    return LanguageMaintainedState.ActivelyMaintained;
  }

  @Override
  protected int getPriorityForId(String id) {
    switch (id) {
      // Rule ids:
      case "OLD_SPELLING_INTERNAL": return 10;
      case "ROCK_N_ROLL": return 1;  // better error than DE_CASE
      case "DE_PROHIBITED_COMPOUNDS": return 1;  // a more detailed error message than from spell checker
      case "ANS_OHNE_APOSTROPH": return 1;
      case "DIESEN_JAHRES": return 1;
      case "EBEN_FALLS": return 1;
      case "UST_ID": return 1;
      case "VER_INF_PKT_VER_INF": return 1; // prefer over DE_CASE
      case "DASS_MIT_VERB": return 1; // prefer over SUBJUNKTION_KOMMA ("Dass wird Konsequenzen haben.")
      case "AB_TEST": return 1; // prefer over spell checker and agreement
      case "BZGL_ABK": return 1; // prefer over spell checker
      case "DURCH_WACHSEN": return 1; // prefer over SUBSTANTIVIERUNG_NACH_DURCHs
      case "RUNDUM_SORGLOS_PAKET": return 1; // higher prio than DE_CASE
      case "MIT_FREUNDLICHEN_GRUESSE": return 1; // higher prio than MEIN_KLEIN_HAUS
      // default is 0
      case "DE_AGREEMENT": return -1;  // prefer RECHT_MACHEN, MONTAGS, KONJUNKTION_DASS_DAS, DESWEITEREN, DIES_BEZUEGLICH and other
      case "MEIN_KLEIN_HAUS": return -1; // prefer more specific rules that offer a suggestion (e.g. DIES_BEZÜGLICH)
      case "COMMA_IN_FRONT_RELATIVE_CLAUSE": return -1; // prefer other rules (KONJUNKTION_DASS_DAS)
      case "MODALVERB_FLEKT_VERB": return -1;
      case "AKZENT_STATT_APOSTROPH": return -1;  // lower prio than PLURAL_APOSTROPH
      case "GERMAN_WORD_REPEAT_RULE": return -1; // prefer other more specific rules
      case "GERMAN_SPELLER_RULE": return -3;  // assume most other rules are more specific and helpful than the spelling rule
      case "AUSTRIAN_GERMAN_SPELLER_RULE": return -3;  // assume most other rules are more specific and helpful than the spelling rule
      case "SWISS_GERMAN_SPELLER_RULE": return -3;  // assume most other rules are more specific and helpful than the spelling rule
      case "PUNCTUATION_PARAGRAPH_END": return -4;  // don't hide spelling mistakes
      case "PUNKT_ENDE_ABSATZ": return -10;  // should never hide other errors, as chance for a false alarm is quite high
      case "KOMMA_ZWISCHEN_HAUPT_UND_NEBENSATZ": return -10;
      case "KOMMA_VOR_RELATIVSATZ": return -10;
      case "COMMA_BEHIND_RELATIVE_CLAUSE": return -10;
      case "TOO_LONG_PARAGRAPH": return -15;
      // Category ids - make sure style issues don't hide overlapping "real" errors:
      case "COLLOQUIALISMS": return -15;
      case "REDUNDANCY": return -15;
      case "GENDER_NEUTRALITY": return -15;
      case "TYPOGRAPHY": return -15;
    }
    if (id.startsWith("CONFUSION_RULE_")) {
      return -1;
    }
    return super.getPriorityForId(id);
  }

}
