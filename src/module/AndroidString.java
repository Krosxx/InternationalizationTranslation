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

package module;

import com.intellij.openapi.project.Project;

import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import Utils.XmlCode;
import data.Log;

/**
 * Created by Wesley Lin on 11/29/14.
 */
public class AndroidString {
    private String key;
    private String value;

    public AndroidString(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public void encodeXmlValue(AndroidString srcStr) {
        if (value != null) {
            int spCount = XmlCode.getTailSpaceCount(srcStr.getValue());
            value = XmlCode.encode(value, spCount);
        }
    }

    public void decodeXmlValue() {
        if (value != null) {
            value = XmlCode.decode(value);
        }
    }

    public AndroidString(AndroidString androidString) {
        this.key = androidString.getKey();
        this.value = androidString.getValue();
    }

    public AndroidString() {
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public void setKey(String key) {
        this.key = key;
    }

    @Override
    public String toString() {
        return "<string name=\"" +
                key +
                "\">" +
                value +
                "</string>";
    }

    private static String SHEADER = "<?xml version=\"1.0\" encoding=\"utf-8\"?>";
    private static String RHEADER = "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">";

    public static List<AndroidString> getAndroidStringsList(Project project, InputStream xml) throws Exception {
        File tmpFile = null;
        if (project != null) {
            tmpFile = new File(project.getBasePath() + "/.idea/trans_tmp.xml");
            tmpFile.createNewFile();
            FileOutputStream fo = new FileOutputStream(tmpFile);

            byte[] bs = new byte[1024];
            int b;
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            while ((b = xml.read(bs)) > 0) {
                bo.write(bs, 0, b);
            }
            String xs = bo.toString("UTF-8");
            String text;
            if (xs.startsWith(SHEADER)) {
                text = xs.replace(SHEADER, RHEADER);
            } else {
                text = RHEADER + xs;
            }
            fo.write(text.getBytes(StandardCharsets.UTF_8));
            fo.close();
            xml.close();
            xml = new FileInputStream(tmpFile);
        }

        List<AndroidString> result = new ArrayList<AndroidString>();
        List<ArrayString> arrayStrings = new ArrayList<ArrayString>();

        SAXReader reader = new SAXReader();
        org.dom4j.Document doc = reader.read(xml);
        Element resNode = doc.getRootElement();

        for (Iterator<Element> it = resNode.elementIterator(); it.hasNext(); ) {
            Element element = it.next();
            String tag = element.getName();
            if (tag.equals("string")) {
                AndroidString as = new AndroidString(
                        element.attributeValue("name", ""),
                        element.getStringValue()
                );
                result.add(as);
                //自动翻译
                //as.decodeXmlValue();
            } else if (tag.equals("string-array")) {
                List<String> items = new ArrayList<>();
                ArrayString ass = new ArrayString(element.attributeValue("name", ""), items);
                arrayStrings.add(ass);
                element.elementIterator().forEachRemaining(new Consumer<Element>() {
                    @Override
                    public void accept(Element element) {
                        items.add(element.getStringValue());
                    }
                });
                //自动翻译
                //ass.decodeXmlValue();
            }
        }
        result.addAll(arrayStrings);
        if (tmpFile != null) {
            tmpFile.delete();
        }
        return result;
    }

    public static List<String> getAndroidStringKeys(List<AndroidString> list) {
        List<String> result = new ArrayList<String>();

        for (int i = 0; i < list.size(); i++) {
            result.add(list.get(i).getKey());
        }
        return result;
    }

    public static String getStringValues(List<AndroidString> list) {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            String value = list.get(i).getValue();
            //value = value.replaceAll("\n", "");
            stringBuilder.append(value);
            if (i != list.size() - 1) {
                stringBuilder.append("\n");
            }
        }
        return stringBuilder.toString();
    }

    public static List<String> getAndroidStringValues(List<AndroidString> list) {
        List<String> result = new ArrayList<String>();

        for (int i = 0; i < list.size(); i++) {
            result.add(list.get(i).getValue());
        }
        return result;
    }


}
