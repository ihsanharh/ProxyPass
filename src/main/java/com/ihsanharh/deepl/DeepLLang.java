package com.ihsanharh.deepl;

public enum DeepLLang {
    DETECT_LANGUAGE("Detect language", "auto"),
    ACEHNESE("Acehnese", "ace"),
    AFRIKAANS("Afrikaans", "af"),
    ALBANIAN("Albanian", "sq"),
    ARABIC("Arabic", "ar"),
    ARAGONESE("Aragonese", "an"),
    ARMENIAN("Armenian", "hy"),
    ASSAMESE("Assamese", "as"),
    AYMARA("Aymara", "ay"),
    AZERBAIJANI("Azerbaijani", "az"),
    BASHKIR("Bashkir", "ba"),
    BASQUE("Basque", "eu"),
    BELARUSIAN("Belarusian", "be"),
    BENGALI("Bengali", "bn"),
    BHOJPURI("Bhojpuri", "bho"),
    BOSNIAN("Bosnian", "bs"),
    BRETON("Breton", "br"),
    BULGARIAN("Bulgarian", "bg"),
    BURMESE("Burmese", "my"),
    CANTONESE("Cantonese", "yue"),
    CATALAN("Catalan", "ca"),
    CEBUANO("Cebuano", "ceb"),
    CHINESE("Chinese", "zh"),
    CROATIAN("Croatian", "hr"),
    CZECH("Czech", "cs"),
    DANISH("Danish", "da"),
    DARI("Dari", "prs"),
    DUTCH("Dutch", "nl"),
    ENGLISH("English", "en"),
    ESPERANTO("Esperanto", "eo"),
    ESTONIAN("Estonian", "et"),
    FINNISH("Finnish", "fi"),
    FRENCH("French", "fr"),
    GALICIAN("Galician", "gl"),
    GEORGIAN("Georgian", "ka"),
    GERMAN("German", "de"),
    GREEK("Greek", "el"),
    GUARANI("Guarani", "gn"),
    GUJARATI("Gujarati", "gu"),
    HAITIAN_CREOLE("Haitian Creole", "ht"),
    HAUSA("Hausa", "ha"),
    HEBREW("Hebrew", "he"),
    HINDI("Hindi", "hi"),
    HUNGARIAN("Hungarian", "hu"),
    ICELANDIC("Icelandic", "is"),
    IGBO("Igbo", "ig"),
    INDONESIAN("Indonesian", "id"),
    IRISH("Irish", "ga"),
    ITALIAN("Italian", "it"),
    JAPANESE("Japanese", "ja"),
    JAVANESE("Javanese", "jv"),
    KAPAMPANGAN("Kapampangan", "pam"),
    KAZAKH("Kazakh", "kk"),
    KONKANI("Konkani", "gom"),
    KOREAN("Korean", "ko"),
    KURDISH_KURMANJI("Kurdish (Kurmanji)", "kmr"),
    KURDISH_SORANI("Kurdish (Sorani)", "ckb"),
    KYRGYZ("Kyrgyz", "ky"),
    LATIN("Latin", "la"),
    LATVIAN("Latvian", "lv"),
    LINGALA("Lingala", "ln"),
    LITHUANIAN("Lithuanian", "lt"),
    LOMBARD("Lombard", "lmo"),
    LUXEMBOURGISH("Luxembourgish", "lb"),
    MACEDONIAN("Macedonian", "mk"),
    MAITHILI("Maithili", "mai"),
    MALAGASY("Malagasy", "mg"),
    MALAY("Malay", "ms"),
    MALAYALAM("Malayalam", "ml"),
    MALTESE("Maltese", "mt"),
    MAORI("Maori", "mi"),
    MARATHI("Marathi", "mr"),
    MONGOLIAN("Mongolian", "mn"),
    NEPALI("Nepali", "ne"),
    NORWEGIAN_BOKM_L("Norwegian (bokmål)", "nb"),
    OCCITAN("Occitan", "oc"),
    OROMO("Oromo", "om"),
    PANGASINAN("Pangasinan", "pag"),
    PASHTO("Pashto", "ps"),
    PERSIAN("Persian", "fa"),
    POLISH("Polish", "pl"),
    PORTUGUESE("Portuguese", "pt-PT"),
    PUNJABI("Punjabi", "pa"),
    QUECHUA("Quechua", "qu"),
    ROMANIAN("Romanian", "ro"),
    RUSSIAN("Russian", "ru"),
    SANSKRIT("Sanskrit", "sa"),
    SERBIAN("Serbian", "sr"),
    SESOTHO("Sesotho", "st"),
    SICILIAN("Sicilian", "scn"),
    SLOVAK("Slovak", "sk"),
    SLOVENIAN("Slovenian", "sl"),
    SPANISH("Spanish", "es-ES"),
    SUNDANESE("Sundanese", "su"),
    SWAHILI("Swahili", "sw"),
    SWEDISH("Swedish", "sv"),
    TAGALOG("Tagalog", "tl"),
    TAJIK("Tajik", "tg"),
    TAMIL("Tamil", "ta"),
    TATAR("Tatar", "tt"),
    TELUGU("Telugu", "te"),
    TSONGA("Tsonga", "ts"),
    TSWANA("Tswana", "tn"),
    TURKISH("Turkish", "tr"),
    TURKMEN("Turkmen", "tk"),
    UKRAINIAN("Ukrainian", "uk"),
    URDU("Urdu", "ur"),
    UZBEK("Uzbek", "uz"),
    VIETNAMESE("Vietnamese", "vi"),
    WELSH("Welsh", "cy"),
    WOLOF("Wolof", "wo"),
    XHOSA("Xhosa", "xh"),
    YIDDISH("Yiddish", "yi"),
    ZULU("Zulu", "zu"),
    CHINESE_SIMPLIFIED("Chinese (simplified)", "zh-Hans"),
    CHINESE_TRADITIONAL("Chinese (traditional)", "zh-Hant"),
    ENGLISH_AMERICAN("English (American)", "en-US"),
    ENGLISH_BRITISH("English (British)", "en-GB"),
    PORTUGUESE_BRAZILIAN("Portuguese (Brazilian)", "pt-BR"),
    SPANISH_LATIN_AMERICAN("Spanish (Latin American)", "es-419");

    private final String uiLabel;
    private final String code;

    DeepLLang(String uiLabel, String code) {
        this.uiLabel = uiLabel;
        this.code = code;
    }

    /**
     * Get the DeepL UI label for this language (e.g. "English (American)" or "Spanish (Latin American)")
     * @return The UI label used by DeepL for this language
     */
    public String getUiLabel() {
        return uiLabel;
    }

    /**
     * Get the DeepL language code for this language (e.g. "en-US" or "es-ES")
     * @return The language code used by DeepL for this language
     */
    public String getCode() {
        return code;
    }

    /**
     * For languages that have multiple variants (e.g. English, Spanish, Portuguese), return a default target variant.
     * For other languages, return itself. DETECT_LANGUAGE cannot be used as a target and will throw an exception.
     * @return The default target language variant for this language
     */
    public DeepLLang asTarget() {
        switch (this) {
            case DETECT_LANGUAGE:
                throw new IllegalArgumentException("Cannot use DETECT_LANGUAGE as a target language!");
            case ENGLISH:
                return ENGLISH_AMERICAN;
            case PORTUGUESE:
                return PORTUGUESE_BRAZILIAN;
            case CHINESE:
                return CHINESE_SIMPLIFIED;
            default:
                return this;
        }
    }

    /**
     * Retrieve a DeepLLang instance based on its code or UI label.
     * @param input The language code or UI label to search for
     * @return The corresponding DeepLLang instance, or null if not found
     */
    public static DeepLLang fromCodeOrLabel(String input) {
        if (input == null || input.trim().isEmpty()) return null;
        
        for (DeepLLang lang : values()) {
            if (lang.code.equalsIgnoreCase(input) || lang.uiLabel.equalsIgnoreCase(input)) {
                return lang;
            }
        }
        
        if (input.equalsIgnoreCase("es")) return SPANISH;
        if (input.equalsIgnoreCase("pt")) return PORTUGUESE;
        if (input.equalsIgnoreCase("zh")) return CHINESE;

        return null;
    }
}
