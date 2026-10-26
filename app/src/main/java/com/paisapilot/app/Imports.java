package com.paisapilot.app;

import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative SMS detection, plus a single lossless CSV interchange format. */
final class Imports {
    static final String[] HEADER={"id","date","title","kind","amount","currency","category","account","owner","note","reviewed","source"};
    private static final Pattern AMOUNT=Pattern.compile("(?i)(?:INR|Rs\\.?|₹)\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)(?![0-9.,])");
    private static final Pattern DEBIT=Pattern.compile("(?i)\\b(debited|spent|paid|withdrawn)\\b");
    private static final Pattern CREDIT=Pattern.compile("(?i)\\b(credited|received|refunded)\\b");
    private static final Pattern SENSITIVE=Pattern.compile("(?i)\\b(otp|one[- ]time|verification|password|authentication)\\b");
    private static final Pattern FUTURE=Pattern.compile("(?i)\\b(will|scheduled|due|offer|eligible|reminder|payable)\\b");
    static JSONObject sms(String body,String sender,String sourceId,String date,String space) {
        if(body==null || body.length()>4000 || (SENSITIVE.matcher(body).find() || FUTURE.matcher(body).find())) return null;
        boolean debit=DEBIT.matcher(body).find(),credit=CREDIT.matcher(body).find();
        if(debit==credit) return null;
        Matcher match=AMOUNT.matcher(body); if(!match.find()) return null;
        String value=match.group(1).replace(",","");
        // Multiple currency amounts are ambiguous (e.g. purchase plus balance); review by hand.
        if(match.find()) return null;
        JSONObject r=Store.record(space,"Transaction");Store.set(r,"id","sms-"+hash(sender+"|"+sourceId));
        Store.set(r,"date",LocalDate.parse(date).toString()); Store.set(r,"title","Imported alert: "+sender);
        Store.set(r,"amount",Engine.parse(value));Store.set(r,"kind",debit?"Expense":"Income");
        Store.set(r,"currency","INR");Store.set(r,"reviewed",false);Store.set(r,"source","sms");
        Store.set(r,"category","Uncategorised");Store.set(r,"account","Unspecified");Store.set(r,"owner","");
        // Raw messages can include account identifiers. Do not persist or export them.
        Store.set(r,"note","Check amount and type against your statement. Transfers, loans and card repayments need reclassification.");
        return r;
    }
    static String hash(String text) {
        try { byte[] bytes=MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();
        } catch(java.security.NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
    }
    private static boolean escapedText(String value) {
        int i=0;while(i<value.length() && value.charAt(i)<=' ')i++;
        String trimmed=value.substring(i);
        return value.startsWith("'") || (!trimmed.isEmpty() && "=+-@\t\r\n".indexOf(trimmed.charAt(0))>=0)
            || value.startsWith("\t") || value.startsWith("\r") || value.startsWith("\n");
    }
    private static String cell(String value) {
        // Prevent spreadsheet formula injection. Prefix text, not numeric amount fields.
        if(escapedText(value)) value="'"+value;
        return "\""+value.replace("\"","\"\"")+"\"";
    }
    static String csv(List<JSONObject> rows) {
        StringBuilder out=new StringBuilder(String.join(",",HEADER)).append("\r\n");
        for(JSONObject r:rows) {
            for(int i=0;i<HEADER.length;i++) {
                if(i>0)out.append(',');String key=HEADER[i];
                String value=key.equals("amount")?Engine.format(Store.amount(r,"amount")):r.optString(key);
                out.append(cell(value));
            }
            out.append("\r\n");
        }
        return out.toString();
    }
    static List<JSONObject> csv(String text,String space) {
        List<List<String>> rows=readCsv(text); List<JSONObject> result=new ArrayList<>();
        if(rows.isEmpty() || !rows.get(0).equals(java.util.Arrays.asList(HEADER)))
            throw new IllegalArgumentException("Use a PaisaPilot transaction CSV with its original columns");
        if(rows.size()>5001)throw new IllegalArgumentException("Import up to 5,000 records at a time");
        for(int i=1;i<rows.size();i++) {
            List<String> row=rows.get(i);if(row.size()!=HEADER.length)throw new IllegalArgumentException("Invalid CSV row "+(i+1));
            JSONObject r=Store.record(space,"Transaction");
            for(int j=0;j<HEADER.length;j++) {
                String value=row.get(j);
                // Reverse only the escaping added by cell(); preserve ordinary apostrophes.
                if(value.length()>1 && value.charAt(0)=='\'' && escapedText(value.substring(1)))value=value.substring(1);
                Store.set(r,HEADER[j],HEADER[j].equals("amount")?Engine.parse(value):value);
            }
            Store.set(r,"reviewed",false); Store.set(r,"source","csv");result.add(r);
        }
        return result;
    }
    static List<List<String>> readCsv(String text) {
        if(text.startsWith("\uFEFF"))text=text.substring(1);
        List<List<String>> rows=new ArrayList<>();List<String> row=new ArrayList<>();StringBuilder cell=new StringBuilder();
        boolean quoted=false,closed=false;
        for(int i=0;i<text.length();i++) {
            char c=text.charAt(i);
            if(quoted) {
                if(c=='"') {if(i+1<text.length()&&text.charAt(i+1)=='"'){cell.append('"');i++;}else{quoted=false;closed=true;}}
                else cell.append(c);
            } else if(c=='"') {
                if(cell.length()!=0||closed)throw new IllegalArgumentException("Unexpected quote in CSV");quoted=true;
            } else if(c==','||c=='\n'||c=='\r') {
                row.add(cell.toString());cell.setLength(0);closed=false;
                if(c!=','){rows.add(row);row=new ArrayList<>();if(c=='\r'&&i+1<text.length()&&text.charAt(i+1)=='\n')i++;}
            } else {if(closed)throw new IllegalArgumentException("Text after closing CSV quote");cell.append(c);}
        }
        if(quoted)throw new IllegalArgumentException("Unclosed CSV quote");
        if(cell.length()>0||!row.isEmpty()||closed){row.add(cell.toString());rows.add(row);}
        return rows;
    }
}
