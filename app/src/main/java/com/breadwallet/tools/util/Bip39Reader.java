package com.breadwallet.tools.util;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Build;
import android.util.Log;

import com.breadwallet.tools.manager.BRReportsManager;
import com.breadwallet.tools.security.BRKeyStore;
import com.breadwallet.tools.security.SmartValidator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * BreadWallet
 * <p/>
 * Created by Mihail Gutan <mihail@breadwallet.com> on 9/28/15.
 * Copyright (c) 2016 breadwallet LLC
 * <p/>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p/>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p/>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

public class Bip39Reader {

    private static final String TAG = Bip39Reader.class.getName();
    public static final int WORD_LIST_SIZE = 2048;
    public static String[] LANGS = {"en", "es", "fr", "it", "ja", "ko", "zh", "tr"};

    //if lang is null then all the lists
    public static List<String> bip39List(Context app, String lang) {

        if (lang == null)
            return getAllWords(app); //return all the words for all langs
        else {
            boolean exists = false;
            for (String s : LANGS) if (s.equalsIgnoreCase(lang)) exists = true;
            if (!exists) {
                lang = "en"; //use en if not a supported lang
            }
        }

        Log.d(TAG, "getWordlist: " + lang);

        return getList(app, lang);
    }

    private static List<String> getAllWords(Context app) {
        List<String> words = new ArrayList<>();
        for (String lang : LANGS) {
            words.addAll(getList(app, lang));
        }
        return words;
    }

    private static List<String> getList(Context app, String lang) {
        String fileName = "words/" + lang + "-BIP39Words.txt";
        List<String> wordList = new ArrayList<>();
        BufferedReader reader = null;
        try {
            AssetManager assetManager = app.getResources().getAssets();
            InputStream inputStream = assetManager.open(fileName);
            reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;

            while ((line = reader.readLine()) != null) {
                wordList.add(cleanWord(line));
            }
        } catch (Exception ex) {
            ex.printStackTrace();

        } finally {
            try {
                if (reader != null)
                    reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (wordList.size() % WORD_LIST_SIZE != 0) {
            BRReportsManager.reportBug(new IllegalArgumentException("The list size should divide by " + WORD_LIST_SIZE), true);
        }
        return new ArrayList<>(wordList);
    }


    public static List<String> detectWords(Context app, String paperKey) {
        if (Utils.isNullOrEmpty(paperKey)) {
            return null;
        }
        String cleanPaperKey = SmartValidator.cleanPaperKey(app, paperKey);
        String[] phraseWords = cleanPaperKey.split(" ");

        for (String s : LANGS) {
            List<String> words = getList(app, s);
            int i = 0;
            for (String word : phraseWords) {
                if (words.contains(word)) i++;
            }
            if (i == phraseWords.length) {
                return words;
            }
        }
        return null;
    }

    public static String detectLang(Context app, String paperKey) {
        if (Utils.isNullOrEmpty(paperKey)) {
            return null;
        }
        String lang = "en";
        String cleanPaperKey = SmartValidator.cleanPaperKey(app, paperKey);
        String firstWord = cleanPaperKey.split(" ")[0];

        for (String s : LANGS) {
            List<String> words = getList(app, s);
            if (words.contains(firstWord)) {
                lang = s;
                break;
            }
        }
        return lang;
    }

    public static String cleanWord(String word) {
        String w = Normalizer.normalize(word.trim().replace("　", "")
                .replace(" ", ""), Normalizer.Form.NFKD);
        return w;
    }

    public static String getLanguage(String languageCode){
        if(languageCode.equalsIgnoreCase("en")) return "english";
        if(languageCode.equalsIgnoreCase("es")) return "spanish";
        if(languageCode.equalsIgnoreCase("fr")) return "french";
        if(languageCode.equalsIgnoreCase("ja")) return "japanese";
        if(languageCode.equalsIgnoreCase("zh")) return "chinese";
        return "english";
    }

    public static String getChineseString() {
        String script = Locale.getDefault().getScript();
        //ISO 15824. Hans	501	Han (Simplified variant)，Hant 502	Han (Traditional variant)
        return "Hans".equals(script) ? "zh" : "tr";
    }
}
