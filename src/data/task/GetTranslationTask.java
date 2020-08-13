/*
 * Copyright 2014-2015 Wesley Lin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package data.task;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import action.ConvertToOtherLanguages;
import data.Log;
import data.SerializeUtil;
import data.StorageDataKey;
import language_engine.TranslationEngineType;
import language_engine.baidu.TransApi;
import module.AndroidString;
import module.ArrayString;
import module.FilterRule;
import module.SupportedLanguages;

/**
 * Created by Wesley Lin on 12/1/14.
 */
public class GetTranslationTask extends Task.Backgroundable {

    private List<SupportedLanguages> selectedLanguages;
    private final List<AndroidString> androidStrings;
    private double indicatorFractionFrame;
    private TranslationEngineType translationEngineType;
    private boolean override;
    private VirtualFile clickedFile;

    private static final String BingIdInvalid = "Invalid client id or client secret, " +
            "please check them <html><a href=\"https://datamarket.azure.com/developer/applications\">here</a></html>";
    private static final String BingQuotaExceeded = "Microsoft Translator quota exceeded, " +
            "please check your data usage <html><a href=\"https://datamarket.azure.com/account/datasets\">here</a></html>";

    private static final String GoogleErrorUnknown = "Error, please check API key in the settings panel.";
    private static final String GoogleDailyLimitError = "Daily Limit Exceeded, please note that Google Translation API " +
            "is a <html><a href=\"https://cloud.google.com/translate/v2/pricing\">paid service.</a></html>";

    private String errorMsg = null;

    public GetTranslationTask(Project project, String title,
                              List<SupportedLanguages> selectedLanguages,
                              List<AndroidString> androidStrings,
                              TranslationEngineType translationEngineType,
                              boolean override,
                              VirtualFile clickedFile) {
        super(project, title);
        this.selectedLanguages = selectedLanguages;
        this.androidStrings = androidStrings;
        this.translationEngineType = translationEngineType;
        this.indicatorFractionFrame = 1.0d / (double) (this.selectedLanguages.size());
        this.override = override;
        this.clickedFile = clickedFile;
    }

    private boolean preCheck() {
        if (translationEngineType == TranslationEngineType.Baidu) {
            PropertiesComponent pc = PropertiesComponent.getInstance();
            String id = pc.getValue(StorageDataKey.BaiduClientIdStored);
            String sec = pc.getValue(StorageDataKey.BaiduClientSecretStored);
            if (id == null || id.isEmpty() || sec == null || sec.isEmpty()) {
                errorMsg = "请在插件设置中配置Baidu翻译 appId & secret";
                return false;
            }
        }
        return true;
    }

    @Override
    public void run(ProgressIndicator indicator) {
        errorMsg = null;
        if (!preCheck()) {
            return;
        }

        try {
            for (int i = 0; i < selectedLanguages.size(); i++) {
                SupportedLanguages language = selectedLanguages.get(i);

                if (language != null) {

                    List<AndroidString> androidStringList = filterAndroidString(androidStrings, language, override);

                    List<List<AndroidString>> filteredAndSplittedString
                            = splitAndroidString(androidStringList, translationEngineType);

                    HashMap<Integer, Integer> errNum = new HashMap<>();

                    List<AndroidString> translationResult = new ArrayList<AndroidString>();
                    for (int j = 0; j < filteredAndSplittedString.size(); j++) {

                        List<AndroidString> strings = getTranslationEngineResult(
                                filteredAndSplittedString.get(j),
                                language,
                                SupportedLanguages.English,
                                translationEngineType
                        );

                        if (strings == null) {
                            Log.i("language===" + language);
                            int jerr = errNum.getOrDefault(j, 0);
                            if (jerr < 5) {
                                errNum.put(j, jerr + 1);
                                j--;
                            } else {
                                if (errorMsg == null) {
                                    errorMsg = "翻译失败：重试次数过多 " + language + "\n";
                                } else {
                                    errorMsg += "翻译失败：重试次数过多 " + language + "\n";
                                }
                                Log.i("翻译失败：重试次数过多" + language);
                                break;
                            }
                            try {
                                // 请求受限
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            continue;
                        }

                        translationResult.addAll(strings);

                        indicator.setFraction(indicatorFractionFrame * (double) (i)
                                + indicatorFractionFrame / filteredAndSplittedString.size() * (double) (j));
                        indicator.setText("Translating to " + language.getLanguageEnglishDisplayName()
                                + " (" + language.getLanguageDisplayName() + ")");

                    }

                    String fileName = getValueResourcePath(language);

                    List<AndroidString> fileContent = getTargetAndroidStrings(androidStrings, translationResult, fileName, override);

                    writeAndroidStringToLocal(myProject, fileName, fileContent);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            errorMsg = e.toString();
        }
    }


    @Override
    public void onSuccess() {

        if (errorMsg == null)
            ConvertToOtherLanguages.showSuccessDialog(getProject(), "translation Success");
        else {
            ConvertToOtherLanguages.showErrorDialog(getProject(), "translation failed: " + errorMsg);
        }
    }

    private String getValueResourcePath(SupportedLanguages language) {
        String resPath = clickedFile.getPath().substring(0,
                clickedFile.getPath().indexOf("/res/") + "/res/".length());

        return resPath + "values-" + language.getAndroidStringFolderNameSuffix()
                + "/" + clickedFile.getName();
    }

    // todo: if got error message, should break the background task
    private List<AndroidString> getTranslationEngineResult(
            @NotNull List<AndroidString> needToTranslatedString,
            @NotNull SupportedLanguages targetLanguageCode,
            @NotNull SupportedLanguages sourceLanguageCode,
            TranslationEngineType translationEngineType) {

        //List<String> querys = AndroidString.getAndroidStringValues(needToTranslatedString);
        //Log.i("query: " + querys.toString());

        List<String> result = null;

        switch (translationEngineType) {
            //case Google:
//                result = GoogleTranslationApi.getTranslationJSON(querys, targetLanguageCode, sourceLanguageCode);
//                if (result == null) {
//                    errorMsg = GoogleErrorUnknown;
//                    return null;
//                } else if (result.isEmpty() && !querys.isEmpty()) {
//                    errorMsg = GoogleDailyLimitError;
//                    return null;
//                }
//                break;
            case Baidu:
                String text = AndroidString.getStringValues(needToTranslatedString);
                Log.i("query: " + text);
                PropertiesComponent pc = PropertiesComponent.getInstance();
                TransApi transApi = new TransApi(
                        pc.getValue(StorageDataKey.BaiduClientIdStored),
                        pc.getValue(StorageDataKey.BaiduClientSecretStored)
                );
                result = transApi.getTransResult(text, "Auto", targetLanguageCode.getLanguageCode());
                break;
        }
        if (result == null) {
            return null;
        }

        List<AndroidString> translatedAndroidStrings = new ArrayList<AndroidString>();

        Log.i("needToTranslatedString.size(): " + needToTranslatedString.size(),
                "result.size(): " + result.size());

        AndroidString tar;
        for (int i = 0; i < needToTranslatedString.size(); i++) {
            tar = needToTranslatedString.get(i);
            if (tar instanceof ArrayString) {
                ArrayString as = new ArrayString(tar.getKey(), result);
                as.encodeXmlValue(tar);
                translatedAndroidStrings.add(as);
            } else {
                AndroidString ts = new AndroidString(tar.getKey(), result.get(i));
                ts.encodeXmlValue(tar);
                translatedAndroidStrings.add(ts);
            }
        }
        return translatedAndroidStrings;
    }


    private List<List<AndroidString>> splitAndroidString(List<AndroidString> origin, TranslationEngineType engineType) {

        List<List<AndroidString>> splited = new ArrayList<List<AndroidString>>();
        int splitFragment = 50;
        switch (engineType) {
            /*case Bing:
                splitFragment = 50;
                break;*/
            //case Google:
            //    splitFragment = 50;
            //    break;
        }
        int i;
        for (i = origin.size() - 1; i >= 0; i--) {
            if (origin.get(i) instanceof ArrayString) {
                splited.add(Collections.singletonList(origin.get(i)));
            } else {
                break;
            }
        }
        if (i < 0) i = 0;
        List<AndroidString> strNodes = origin.subList(0, i + 1);

        if (strNodes.size() > 0) {
            if (strNodes.size() <= splitFragment) {
                splited.add(strNodes);
            } else {
                int count = (strNodes.size() % splitFragment == 0) ? (strNodes.size() / splitFragment) : (strNodes.size() / splitFragment + 1);
                for (i = 1; i <= count; i++) {
                    int end = i * splitFragment;
                    if (end > strNodes.size()) {
                        end = strNodes.size();
                    }

                    splited.add(strNodes.subList((i - 1) * splitFragment, end));
                }
            }
        }

        return splited;
    }

    private List<AndroidString> filterAndroidString(List<AndroidString> origin,
                                                    SupportedLanguages language,
                                                    boolean override) {
        List<AndroidString> result = new ArrayList<AndroidString>();

        String rulesString = PropertiesComponent.getInstance().getValue(StorageDataKey.SettingFilterRules);
        List<FilterRule> filterRules = new ArrayList<FilterRule>();
        if (rulesString == null) {
            filterRules.add(FilterRule.DefaultFilterRule);
        } else {
            filterRules = SerializeUtil.deserializeFilterRuleList(rulesString);
        }
//        Log.i("targetAndroidString: " + targetAndroidStrings.toString());
        for (AndroidString androidString : origin) {
            // filter rules
            if (FilterRule.inFilterRule(androidString.getKey(), filterRules)) {
                Log.i("Filter: " + androidString.getKey());
                continue;
            }

            // override
            /*if (!override && !targetAndroidStrings.isEmpty()) {
                // check if there is the androidString in this file
                // if there is, filter it
                if (isAndroidStringListContainsKey(targetAndroidStrings, androidString.getKey())) {
                    continue;
                }
            }*/

            result.add(androidString);
        }

        return result;
    }

    private List<AndroidString> getTargetAndroidStrings(
            List<AndroidString> sourceAndroidStrings,
            List<AndroidString> translatedAndroidStrings,
            String fileName,
            boolean override) {

        if (translatedAndroidStrings == null) {
            translatedAndroidStrings = new ArrayList<AndroidString>();
        }

        VirtualFile existenceFile = LocalFileSystem.getInstance().findFileByPath(fileName);
        List<AndroidString> existenceAndroidStrings = null;
        if (existenceFile != null && !override) {
            try {
                existenceAndroidStrings = AndroidString.getAndroidStringsList(myProject, existenceFile.getInputStream());
            } catch (Exception e) {
                existenceAndroidStrings = new ArrayList<AndroidString>();
                e.printStackTrace();
            }
        } else {
            existenceAndroidStrings = new ArrayList<AndroidString>();
        }

        Log.i("sourceAndroidStrings: " + sourceAndroidStrings,
                "translatedAndroidStrings: " + translatedAndroidStrings,
                "existenceAndroidStrings: " + existenceAndroidStrings);

        List<AndroidString> targetAndroidStrings = new ArrayList<AndroidString>();

        for (AndroidString string : sourceAndroidStrings) {
            AndroidString resultString;
            if (string instanceof ArrayString) {
                resultString = new ArrayString((ArrayString) string);
                if (!override) {
                    ArrayString existenceValue = (ArrayString) getAndroidStringValueInList(existenceAndroidStrings, resultString.getKey());
                    if (existenceValue != null) {
                        ((ArrayString) resultString).setItems(existenceValue.getItems());
                    }
                }

                ArrayString translatedValue = (ArrayString) getAndroidStringValueInList(translatedAndroidStrings, resultString.getKey());
                if (translatedValue != null) {
                    ((ArrayString) resultString).setItems(translatedValue.getItems());
                }

            } else {

                resultString = new AndroidString(string);

                // if override is checked, skip setting the existence value, for performance issue
                if (!override) {
                    AndroidString existenceValue = getAndroidStringValueInList(existenceAndroidStrings, resultString.getKey());
                    if (existenceValue != null) {
                        resultString.setValue(existenceValue.getValue());
                    }
                }

                AndroidString translatedValue = getAndroidStringValueInList(translatedAndroidStrings, resultString.getKey());
                if (translatedValue != null) {
                    resultString.setValue(translatedValue.getValue());
                }
            }

            targetAndroidStrings.add(resultString);
        }
        Log.i("targetAndroidStrings: " + targetAndroidStrings);
        return targetAndroidStrings;
    }

    private static void writeAndroidStringToLocal(
            final Project myProject,
            String filePath,
            List<AndroidString> fileContent
    ) {
        File file = new File(filePath);
        final VirtualFile virtualFile;
        boolean fileExits = true;
        try {
            file.getParentFile().mkdirs();
            if (!file.exists()) {
                fileExits = false;
                file.createNewFile();
            }
            //Change by GodLikeThomas FIX: Appeared Messy code under windows --start;
            //FileWriter fileWriter = new FileWriter(file.getAbsoluteFile());
            //BufferedWriter writer = new BufferedWriter(fileWriter);
            //writer.write(getFileContent(fileContent));
            //writer.close();
            FileOutputStream fos = new FileOutputStream(file.getAbsoluteFile());
            OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF-8");
            osw.write(getFileContent(fileContent));
            osw.close();
            //Change by GodLikeThomas FIX: Appeared Messy code under windows --end;
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (fileExits) {
            virtualFile = LocalFileSystem.getInstance().findFileByIoFile(file);
            if (virtualFile == null)
                return;
            virtualFile.refresh(true, false, new Runnable() {
                @Override
                public void run() {
                    openFileInEditor(myProject, virtualFile);
                }
            });
        } else {
            virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
            openFileInEditor(myProject, virtualFile);
        }
    }

    private static void openFileInEditor(final Project myProject, @Nullable final VirtualFile file) {
        if (file == null)
            return;

        // run in UI thread:
        //    https://theantlrguy.atlassian.net/wiki/display/~admin/Intellij+plugin+development+notes#Intellijplugindevelopmentnotes-GUIandthreads,backgroundtasks
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
                final FileEditorManager editorManager = FileEditorManager.getInstance(myProject);
                editorManager.openFile(file, true);
            }
        });
    }

    private static String getFileContent(List<AndroidString> fileContent) {
        String xmlHeader = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n";
        String stringResourceHeader = "<resources>\n\n";
        String stringResourceTail = "</resources>\n";

        StringBuilder sb = new StringBuilder();
        sb.append(xmlHeader).append(stringResourceHeader);
        for (AndroidString androidString : fileContent) {
            sb.append("\t").append(androidString.toString()).append("\n");
        }
        sb.append("\n").append(stringResourceTail);
        return sb.toString();
    }

    private static boolean isAndroidStringListContainsKey(List<AndroidString> androidStrings, String key) {
        List<String> keys = AndroidString.getAndroidStringKeys(androidStrings);
        return keys.contains(key);
    }

    public static AndroidString getAndroidStringValueInList(List<AndroidString> androidStrings, String key) {
        for (AndroidString androidString : androidStrings) {
            if (androidString.getKey().equals(key)) {
                return androidString;
            }
        }
        return null;
    }

}
