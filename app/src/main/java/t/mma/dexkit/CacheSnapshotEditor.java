package t.mma.dexkit;

import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Complete cache snapshot: compare before opening a persistent editor. */
final class CacheSnapshotEditor implements SharedPreferences.Editor {
    private final SharedPreferences prefs;
    private final Map<String, Object> values = new HashMap<>();
    CacheSnapshotEditor(SharedPreferences prefs) { this.prefs = prefs; }
    public SharedPreferences.Editor putString(String k, String v) { if (v == null) values.remove(k); else values.put(k, v); return this; }
    public SharedPreferences.Editor putStringSet(String k, Set<String> v) { if (v == null) values.remove(k); else values.put(k, new HashSet<>(v)); return this; }
    public SharedPreferences.Editor putInt(String k, int v) { values.put(k, v); return this; }
    public SharedPreferences.Editor putLong(String k, long v) { values.put(k, v); return this; }
    public SharedPreferences.Editor putFloat(String k, float v) { values.put(k, v); return this; }
    public SharedPreferences.Editor putBoolean(String k, boolean v) { values.put(k, v); return this; }
    public SharedPreferences.Editor remove(String k) { values.remove(k); return this; }
    public SharedPreferences.Editor clear() { values.clear(); return this; }
    @SuppressWarnings("unchecked")
    private SharedPreferences.Editor changedEditor() {
        if (values.equals(prefs.getAll())) return null;
        SharedPreferences.Editor editor = prefs.edit().clear();
        for (Map.Entry<String, Object> item : values.entrySet()) {
            String key = item.getKey(); Object value = item.getValue();
            if (value instanceof String) editor.putString(key, (String) value);
            else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
            else if (value instanceof Integer) editor.putInt(key, (Integer) value);
            else if (value instanceof Long) editor.putLong(key, (Long) value);
            else if (value instanceof Float) editor.putFloat(key, (Float) value);
            else if (value instanceof Set) editor.putStringSet(key, (Set<String>) value);
            else throw new IllegalStateException("Unsupported cache value: " + key);
        }
        return editor;
    }
    public void apply() { SharedPreferences.Editor editor = changedEditor(); if (editor != null) editor.apply(); }
    public boolean commit() { SharedPreferences.Editor editor = changedEditor(); return editor == null || editor.commit(); }
}
