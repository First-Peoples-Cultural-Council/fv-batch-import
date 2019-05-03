package mappers.firstvoices;

import mappers.propertyreaders.PropertyReader;
import reader.AbstractReader;

public class PartOfSpeechPropertyReader extends PropertyReader {

    @Override
    public String getValue(AbstractReader reader) {
        switch (reader.getString(column)) {
        case "0":
            case "basic": return "basic";
        case "1":
            case "verb": return "verb";
        case "2":
            case "noun": return "noun";
        case "3":
            case "pronoun": return "pronoun";
        case "4":
            case "adjective": return "adjective";
        case "5":
            case "adverb": return "adverb";
        case "6":
            case "preposition": return "preposition";
        case "7":
            case "conjunction": return "conjunction";
        case "8":
            case "interjection": return "interjection";
        case "9":
            case "particle": return "particle";
        case "10":
            case "advanced": return "advanced";
        case "11":
            case "pronoun_personal": return "pronoun_personal";
        case "12":
            case "pronoun_reflexive": return "pronoun_reflexive";
        case "13":
            case "pronoun_reciprocal": return "pronoun_reciprocal";
        case "14":
            case "pronoun_demonstrative": return "pronoun_demonstrative";
        case "15":
            case "pronoun_relative": return "pronoun_relative";
        case "16":
            case "particle_postposition": return "particle_postposition";
        case "17":
            case "particle_quantifier": return "particle_quantifier";
        case "18":
            case "particle_article_determiner": return "particle_article_determiner";
        case "19":
            case "particle_tense_aspect": return "particle_tense_aspect";
        case "20":
            case "particle_modal": return "particle_modal";
        case "21":
            case "particle_conjunction": return "particle_conjunction";
        case "22":
            case "particle_auxiliary_verb": return "particle_auxiliary_verb";
        case "23":
            case "particle_adjective": return "particle_adjective";
        case "24":
            case "particle_adverb": return "particle_adverb";
        case "25":
            case "entity_noun_like_word": return "entity_noun_like_word";
        case "26":
            case "event_activity_verb_like_word": return "event_activity_verb_like_word";
        case "27":
            case "event_activity_verb_like_word_transitive": return "event_activity_verb_like_word_transitive";
        case "28":
            case "event_activity_verb_like_word_intransitive": return "event_activity_verb_like_word_intransitive";
        case "29":
            case "event_activity_verb_like_word_reflexive": return "event_activity_verb_like_word_reflexive";
        case "30":
            case "event_activity_verb_like_word_reciprocal": return "event_activity_verb_like_word_reciprocal";
        case "31":
            case "question_word": return "question_word";
        case "32":
            case "suffix_prefix": return "suffix_prefix";
        default:
            System.out.println("Could not retrieve Nuxeo part of speech value");
            case "": return "";
        }
    }

    public PartOfSpeechPropertyReader(String key, Object column) {
        super(key, column);
    }

}
