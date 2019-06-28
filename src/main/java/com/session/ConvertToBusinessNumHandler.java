package com.session;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.fc.runtime.Context;
import com.aliyun.fc.runtime.PojoRequestHandler;
import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.dictionary.py.Pinyin;
import com.hankcs.hanlp.seg.Segment;
import com.hankcs.hanlp.seg.common.Term;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConvertToBusinessNumHandler implements PojoRequestHandler<JSONObject, JSONObject> {
    @Override
    public JSONObject handleRequest(JSONObject eventObj, Context context) {
        /**
         *  eventObj structure definition
         *
         *  read-only variables
         *  "environment": "Object",
         *  "lastOutputForFunction": "String",
         *  "slotSummary": "Object",
         *
         *  read/write variables
         *  "global": "Object",
         *  "overrideResponse": "Object",
         *  "functionOutput": "String",
         *  "routeVariable": "String"
         */

        String[] num_pinyin = {"ling2", "yi1", "er4", "san1", "si4", "wu3", "liu4", "qi1", "ba1", "jiu3", "", "yao1", "liang3"};
        Map num_map = new HashMap();
        for (int i = 0; i < num_pinyin.length; i++) {
            num_map.put(num_pinyin[i],i%10);
        }
        num_map.put("ge4","个");

        JSONObject slots = eventObj.getJSONObject("slotSummary");
        String slotValue = slots.getString("业务号码.业务号码");
        String business_num = "";
        eventObj.put("routeVariable", "0");

        // 将中文转为拼音，再使用拼音转换为数字
        StringBuilder stringBuilder = new StringBuilder();
        List<Pinyin> pinyinList = HanLP.convertToPinyinList(slotValue);

        for (int i = 0; i < pinyinList.size(); i++) {
            String pingyin = pinyinList.get(i).toString();
            if (num_map.containsKey(pingyin)) {
                stringBuilder.append(num_map.get(pingyin));
            } else {
                stringBuilder.append(slotValue.charAt(i));
            }
        }

        // 将‘3个2’转换为‘222’
        int index = stringBuilder.indexOf("个",1);
        while (index > 0 && index < stringBuilder.length()-1) {
            if (Character.isDigit(stringBuilder.charAt(index-1)) &&
                    Character.isDigit(stringBuilder.charAt(index+1)) &&
                    Integer.parseInt(""+stringBuilder.charAt(index-1)) > 0
            ) {
                int num = Integer.parseInt(""+stringBuilder.charAt(index-1));
                String numStr = String.join("", Collections.nCopies(num, ""+stringBuilder.charAt(index+1)));
                stringBuilder.delete(index-1,index+2);
                stringBuilder.insert(index-1,numStr);
            }
            index = stringBuilder.indexOf("个",index+1);
        }

        // 正则匹配找到电话号码
        String numRegex = "\\d{1,}";
        String rgex = "(0\\d{2,3}(-)?)?(\\d{8}|\\d{7})";
        Pattern numPattern = Pattern.compile(numRegex);// 匹配的模式
        Pattern pattern = Pattern.compile(rgex);// 匹配的模式
        Matcher nums = numPattern.matcher(stringBuilder);

        while(nums.find()) {
            String num = nums.group(0);
            Matcher linkNum = pattern.matcher(num);
            if (linkNum.find()){
                String rnum = linkNum.group(0);
                if (num.equals(rnum)) {
                    business_num = rnum;
                    break;
                }
            }
        }

        JSONObject global_params = eventObj.getJSONObject("global");
        global_params.put("business_num", business_num);
        eventObj.put("global", global_params);
        if (business_num.length() > 0) {
            eventObj.put("routeVariable", "1");
        }

        return eventObj;
    }
}
