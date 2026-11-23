package com.example.dailybalance;

import android.content.Context;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Best-effort offline-first sync. The first sync is additive: it unions local and cloud records
 * by stable ID and honors deletion tombstones before uploading the merged result.
 */
final class SyncManager {
    private static boolean syncing;
    private static final ExecutorService SYNC_EXECUTOR = Executors.newSingleThreadExecutor();
    private SyncManager() {}

    static synchronized boolean isSyncing() { return syncing; }

    static Task<Void> sync(Context context) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return Tasks.forException(new IllegalStateException("Sign in to sync Batwa."));
        synchronized (SyncManager.class) {
            if (syncing) return Tasks.forResult(null);
            syncing = true;
        }

        CollectionReference root = FirebaseFirestore.getInstance()
                .collection("users").document(user.getUid()).collection("batwa");
        Task<List<Object>> reads = Tasks.whenAllSuccess(
                root.document("transactions").collection("items").get(),
                root.document("tags").collection("items").get(),
                root.document("deletedTransactions").collection("items").get(),
                root.document("deletedTags").collection("items").get());

        return reads.continueWithTask(SYNC_EXECUTOR, task -> {
            if (!task.isSuccessful()) throw task.getException() == null
                    ? new IllegalStateException("Could not read Batwa cloud data.") : task.getException();
            List<?> results = task.getResult();
            JSONArray transactions = snapshots((com.google.firebase.firestore.QuerySnapshot) results.get(0));
            JSONArray tags = snapshots((com.google.firebase.firestore.QuerySnapshot) results.get(1));
            JSONArray deletedTransactions = snapshots((com.google.firebase.firestore.QuerySnapshot) results.get(2));
            JSONArray deletedTags = snapshots((com.google.firebase.firestore.QuerySnapshot) results.get(3));

            Store.mergeCloudData(context, transactions, tags, deletedTransactions, deletedTags);
            return uploadLocal(context, root);
        }).addOnCompleteListener(task -> {
            synchronized (SyncManager.class) { syncing = false; }
        });
    }

    private static JSONArray snapshots(com.google.firebase.firestore.QuerySnapshot snapshot) {
        JSONArray array = new JSONArray();
        for (DocumentSnapshot document : snapshot.getDocuments()) {
            Map<String, Object> source = document.getData();
            if (source == null) continue;
            Map<String, Object> data = new HashMap<>(source);
            if (!data.containsKey("id")) data.put("id", document.getId());
            array.put(new JSONObject(data));
        }
        return array;
    }

    private static Task<Void> uploadLocal(Context context, CollectionReference root) {
        List<Task<Void>> commits = new ArrayList<>();
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        WriteBatch[] batch = new WriteBatch[]{db.batch()};
        int[] count = new int[]{0};
        addWrites(commits, batch, count, root.document("transactions").collection("items"), Store.transactions(context));
        addWrites(commits, batch, count, root.document("tags").collection("items"), Store.tags(context));
        addWrites(commits, batch, count, root.document("deletedTransactions").collection("items"), Store.deletedTransactionTombstones(context));
        addWrites(commits, batch, count, root.document("deletedTags").collection("items"), Store.deletedTagTombstones(context));
        if (count[0] > 0) commits.add(batch[0].commit());
        return Tasks.whenAll(commits);
    }

    // Firestore limits each batch to 500 writes; use 450 to leave room for future metadata writes.
    private static void addWrites(List<Task<Void>> commits, WriteBatch[] batch, int[] count,
                                  CollectionReference collection, JSONArray records) {
        for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.optJSONObject(i);
            if (record == null) continue;
            String id = record.optString("id", "");
            if (id.isEmpty()) continue;
            if (count[0] == 450) {
                commits.add(batch[0].commit());
                batch[0] = FirebaseFirestore.getInstance().batch();
                count[0] = 0;
            }
            batch[0].set(collection.document(id), recordToMap(record), SetOptions.merge());
            count[0]++;
        }
    }

    private static Map<String, Object> recordToMap(JSONObject record) {
        Map<String, Object> map = new HashMap<>();
        java.util.Iterator<String> keys = record.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = record.opt(key);
            if (value != JSONObject.NULL) map.put(key, value);
        }
        return map;
    }
}
