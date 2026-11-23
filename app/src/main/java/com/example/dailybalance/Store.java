package com.example.dailybalance;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/** Local persistence. Preference file and original keys intentionally remain unchanged for upgrades. */
public class Store {
    private static final String PREF = "daily_balance_store";
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREF, Context.MODE_PRIVATE); }
    static boolean isSetup(Context c) { return prefs(c).getBoolean("setup", false); }
    static double balance(Context c) { return Double.longBitsToDouble(prefs(c).getLong("balance", Double.doubleToLongBits(0))); }
    static void setSetup(Context c, double opening) {
        // The opening amount establishes the balance; it must not be added to it a second time.
        prefs(c).edit().putBoolean("setup", true)
                .putLong("opening_balance", Double.doubleToLongBits(opening))
                .putLong("balance", Double.doubleToLongBits(opening)).apply();
        JSONArray old = transactions(c), next = new JSONArray();
        try {
            JSONObject item = new JSONObject(); item.put("id", UUID.randomUUID().toString()); item.put("createdAt", System.currentTimeMillis()); item.put("updatedAt", System.currentTimeMillis()); item.put("description", "Starting balance");
            item.put("amount", opening); item.put("time", System.currentTimeMillis()); item.put("tag", ""); next.put(item);
            for (int i = 0; i < old.length(); i++) next.put(old.getJSONObject(i));
        } catch (Exception ignored) {}
        prefs(c).edit().putString("transactions", next.toString()).putBoolean("opening_balance_migrated", true).apply();
    }

    /** Repairs the v1.1 setup bug once: the opening amount was written to balance and then added again. */
    static void migrateOpeningBalance(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.getBoolean("setup", false) || p.getBoolean("opening_balance_migrated", false)) return;
        JSONArray tx = transactions(c);
        for (int i = 0; i < tx.length(); i++) {
            JSONObject o = tx.optJSONObject(i);
            if (o != null && "Starting balance".equalsIgnoreCase(o.optString("description", ""))) {
                double opening = o.optDouble("amount", 0);
                double corrected = balance(c) - opening;
                p.edit().putLong("balance", Double.doubleToLongBits(corrected))
                        .putLong("opening_balance", Double.doubleToLongBits(opening))
                        .putBoolean("opening_balance_migrated", true).apply();
                return;
            }
        }
        p.edit().putLong("opening_balance", Double.doubleToLongBits(balance(c)))
                .putBoolean("opening_balance_migrated", true).apply();
    }

    static double openingBalance(Context c) {
        return Double.longBitsToDouble(prefs(c).getLong("opening_balance", Double.doubleToLongBits(balance(c))));
    }

    /** Changes the baseline by the difference, preserving all later expenses and credits. */
    static void changeOpeningBalance(Context c, double opening) {
        double oldOpening = openingBalance(c);
        double adjustedBalance = balance(c) + (opening - oldOpening);
        JSONArray old = transactions(c), next = new JSONArray();
        boolean updated = false;
        for (int i = 0; i < old.length(); i++) {
            JSONObject o = old.optJSONObject(i);
            if (o == null) continue;
            if (!updated && "Starting balance".equalsIgnoreCase(o.optString("description", ""))) {
                try { o.put("amount", opening); } catch (Exception ignored) {}
                updated = true;
            }
            next.put(o);
        }
        if (!updated) {
            try { JSONObject item = new JSONObject(); item.put("description", "Starting balance"); item.put("amount", opening); item.put("time", System.currentTimeMillis()); item.put("tag", ""); next.put(item); } catch (Exception ignored) {}
        }
        prefs(c).edit().putLong("opening_balance", Double.doubleToLongBits(opening))
                .putLong("balance", Double.doubleToLongBits(adjustedBalance))
                .putString("transactions", next.toString()).apply();
    }
    /** Add stable IDs to legacy rows without changing existing transaction data. */
    static synchronized JSONArray transactions(Context c) {
        JSONArray result;
        try { result = new JSONArray(prefs(c).getString("transactions", "[]")); }
        catch (Exception e) { result = new JSONArray(); }
        boolean changed = false;
        for (int i = 0; i < result.length(); i++) {
            JSONObject item = result.optJSONObject(i);
            if (item == null || !item.optString("id", "").trim().isEmpty()) continue;
            try {
                item.put("id", UUID.randomUUID().toString());
                item.put("createdAt", item.optLong("time", System.currentTimeMillis()));
                item.put("updatedAt", System.currentTimeMillis());
                changed = true;
            } catch (Exception ignored) {}
        }
        if (changed) prefs(c).edit().putString("transactions", result.toString())
                .putBoolean("transaction_ids_migrated", true).apply();
        return result;
    }

    static JSONArray deletedTransactionTombstones(Context c) {
        try { return new JSONArray(prefs(c).getString("deleted_transaction_ids", "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    private static void recordDeletedTransaction(Context c, String id) {
        if (id == null || id.isEmpty()) return;
        JSONArray deleted = deletedTransactionTombstones(c);
        try {
            JSONObject tombstone = new JSONObject();
            tombstone.put("id", id);
            tombstone.put("deletedAt", System.currentTimeMillis());
            deleted.put(tombstone);
            prefs(c).edit().putString("deleted_transaction_ids", deleted.toString()).apply();
        } catch (Exception ignored) {}
    }
    static void addTransaction(Context c, String description, double amount, long time) { addTransaction(c, description, amount, time, ""); }
    static void addTransaction(Context c, String description, double amount, long time, String tag) {
        JSONArray old = transactions(c); JSONArray next = new JSONArray();
        try {
            JSONObject item = new JSONObject(); item.put("id", UUID.randomUUID().toString()); item.put("createdAt", System.currentTimeMillis()); item.put("updatedAt", System.currentTimeMillis()); item.put("description", description); item.put("amount", amount); item.put("time", time); item.put("tag", tag == null ? "" : tag.trim()); next.put(item);
            for (int i = 0; i < old.length(); i++) next.put(old.getJSONObject(i));
        } catch (Exception ignored) {}
        double newBalance = balance(c) + amount;
        prefs(c).edit().putLong("balance", Double.doubleToLongBits(newBalance)).putString("transactions", next.toString()).apply();
    }
    /** Update a transaction and apply only the amount difference to the running balance. */
    static void updateTransaction(Context c, int index, String description, double amount, long time, String tag) {
        JSONArray old=transactions(c), next=new JSONArray();
        double delta=0;
        for(int i=0;i<old.length();i++){
            JSONObject item=old.optJSONObject(i);if(item==null)continue;
            if(i==index){
                delta=amount-item.optDouble("amount",0);
                try{item.put("description",description);item.put("amount",amount);item.put("time",time);item.put("tag",tag==null?"":tag.trim());item.put("updatedAt",System.currentTimeMillis());}catch(Exception ignored){}
            }
            next.put(item);
        }
        java.util.List<JSONObject> sorted=new java.util.ArrayList<>();
        for(int i=0;i<next.length();i++){JSONObject item=next.optJSONObject(i);if(item!=null)sorted.add(item);}
        java.util.Collections.sort(sorted,(a,b)->Long.compare(b.optLong("time",0),a.optLong("time",0)));
        JSONArray ordered=new JSONArray();for(JSONObject item:sorted)ordered.put(item);
        prefs(c).edit().putLong("balance",Double.doubleToLongBits(balance(c)+delta)).putString("transactions",ordered.toString()).apply();
    }

    /** Delete a transaction and reverse its contribution to the running balance. */
    static void deleteTransaction(Context c, int index) {
        JSONArray old=transactions(c), next=new JSONArray();double removed=0;
        for(int i=0;i<old.length();i++){
            JSONObject item=old.optJSONObject(i);if(item==null)continue;
            if(i==index){removed=item.optDouble("amount",0);recordDeletedTransaction(c,item.optString("id",""));}else next.put(item);
        }
        prefs(c).edit().putLong("balance",Double.doubleToLongBits(balance(c)-removed)).putString("transactions",next.toString()).apply();
    }

    static void renameTagInTransactions(Context c, String oldName, String newName) {
        JSONArray old=transactions(c), next=new JSONArray();
        for(int i=0;i<old.length();i++) try { JSONObject o=old.getJSONObject(i); if(o.optString("tag","").equalsIgnoreCase(oldName)) o.put("tag",newName); next.put(o); } catch(Exception ignored){}
        prefs(c).edit().putString("transactions",next.toString()).apply();
    }
    static void renameTag(Context c, String oldName, String newName, String icon) {
        JSONArray a=tags(c); boolean found=false;
        for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null&&o.optString("name").equalsIgnoreCase(oldName)){try{o.put("name",newName);o.put("icon",icon);}catch(Exception ignored){}found=true;break;}}
        if(!found){try{JSONObject o=new JSONObject();o.put("name",newName);o.put("icon",icon);a.put(o);}catch(Exception ignored){}}
        prefs(c).edit().putString("tags",a.toString()).apply();
        renameTagInTransactions(c,oldName,newName);
    }
    static void clearTagFromTransactions(Context c, String name) {
        JSONArray old=transactions(c), next=new JSONArray();
        for(int i=0;i<old.length();i++) try { JSONObject o=old.getJSONObject(i); if(o.optString("tag","").equalsIgnoreCase(name)) o.put("tag",""); next.put(o); } catch(Exception ignored){}
        prefs(c).edit().putString("transactions",next.toString()).apply();
    }
    static synchronized JSONArray tags(Context c) {
        JSONArray result;
        try { result = new JSONArray(prefs(c).getString("tags", "[]")); } catch(Exception e) { result = new JSONArray(); }
        boolean changed = false;
        for (int i=0;i<result.length();i++) {
            JSONObject item=result.optJSONObject(i);
            if(item==null || !item.optString("id","").trim().isEmpty()) continue;
            try { item.put("id",UUID.randomUUID().toString()); item.put("updatedAt",System.currentTimeMillis()); changed=true; }
            catch(Exception ignored) {}
        }
        if(changed) prefs(c).edit().putString("tags",result.toString()).putBoolean("tag_ids_migrated",true).apply();
        return result;
    }

    static JSONArray deletedTagTombstones(Context c) {
        try { return new JSONArray(prefs(c).getString("deleted_tag_ids", "[]")); }
        catch(Exception e) { return new JSONArray(); }
    }
    static String tagIcon(Context c, String name) {
        JSONArray a=tags(c); for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i); if(o!=null && o.optString("name").equalsIgnoreCase(name)) return o.optString("icon","🏷️");} return "🏷️";
    }
    static boolean hasTag(Context c,String name) { JSONArray a=tags(c); for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i); if(o!=null&&o.optString("name").equalsIgnoreCase(name.trim()))return true;} return false; }
    static void saveTag(Context c,String name,String icon) {
        name=name.trim(); JSONArray a=tags(c);
        for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i); if(o!=null&&o.optString("name").equalsIgnoreCase(name)){try{o.put("icon",icon);}catch(Exception ignored){} prefs(c).edit().putString("tags",a.toString()).apply();return;}}
        try {JSONObject o=new JSONObject();o.put("name",name);o.put("icon",icon);a.put(o);}catch(Exception ignored){}
        prefs(c).edit().putString("tags",a.toString()).apply();
    }
    static void deleteTag(Context c,String name) {
        JSONArray a=tags(c), next=new JSONArray();
        for(int i=0;i<a.length();i++){
            JSONObject o=a.optJSONObject(i);
            if(o==null) continue;
            if(o.optString("name").equalsIgnoreCase(name)) {
                try {
                    JSONArray deleted=deletedTagTombstones(c);
                    JSONObject tombstone=new JSONObject();
                    tombstone.put("id",o.optString("id",""));
                    tombstone.put("deletedAt",System.currentTimeMillis());
                    deleted.put(tombstone);
                    prefs(c).edit().putString("deleted_tag_ids",deleted.toString()).apply();
                } catch(Exception ignored) {}
            } else next.put(o);
        }
        prefs(c).edit().putString("tags",next.toString()).apply();
        clearTagFromTransactions(c,name);
    }
    static String money(double n) { return "PKR " + String.format(Locale.US, "%,.2f", n); }
    static String date(long millis) { return new SimpleDateFormat("dd-MM-yy hh:mm a", Locale.US).format(new Date(millis)); }
    static double dailyAmount(Context c) { return Double.longBitsToDouble(prefs(c).getLong("daily_amount", Double.doubleToLongBits(0))); }
    static int dailyHour(Context c) { return prefs(c).getInt("daily_hour", 0); }
    static int dailyMinute(Context c) { return prefs(c).getInt("daily_minute", 0); }
    static boolean dailyEnabled(Context c) { return prefs(c).getBoolean("daily_enabled", false); }
    static void saveDaily(Context c, double amount, int hour, int minute) {
        prefs(c).edit().putLong("daily_amount", Double.doubleToLongBits(amount)).putInt("daily_hour", hour).putInt("daily_minute", minute).putBoolean("daily_enabled", true).apply();
    }
    static String lastCreditDate(Context c) { return prefs(c).getString("last_credit_date", ""); }
    static void setLastCreditDate(Context c, String date) { prefs(c).edit().putString("last_credit_date", date).apply(); }
}
