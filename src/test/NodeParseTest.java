package test;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;

import module.AndroidString;

/**
 * Created by 11324.
 * Date: 2020/8/13
 */
public class NodeParseTest {
    public static void main(String[] args) throws Exception {

        File asFile = new File("F:\\Project\\IntellijProject\\BaiduTranslationInternationalization\\test.xml");
        List<AndroidString> ss=  AndroidString.getAndroidStringsList(null, new FileInputStream(asFile));


        System.out.println(ss);

        for (AndroidString s : ss) {
            s.encodeXmlValue(s);
            System.out.println(s);
        }


    }
}
