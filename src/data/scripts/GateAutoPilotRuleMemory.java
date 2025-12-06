package data.scripts;

import java.util.*;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

public class GateAutoPilotRuleMemory implements MemoryAPI {
    private final Map<String, Object> map = new HashMap<>();

    @Override
    public void addRequired(String arg0, String arg1) {
        // No-op: requirement tracking not needed for this wrapper
    }

    @Override
    public void advance(float arg0) {
        // No-op: timing logic not required for this wrapper
    }

    @Override
    public boolean between(String arg0, float arg1, float arg2) {
        float value = getFloat(arg0);
        return value >= arg1 && value <= arg2;
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public boolean contains(String arg0) {
        return map.containsKey(arg0);
    }

    @Override
    public void expire(String arg0, float arg1) {
        // No-op: expiration not tracked
    }

    @Override
    public Object get(String arg0) {
        return map.get(arg0);
    }

    @Override
    public boolean getBoolean(String arg0) {
        return (boolean) map.get(arg0);
    }

    @Override
    public SectorEntityToken getEntity(String arg0) {
        return (SectorEntityToken) map.get(arg0);
    }

    @Override
    public float getExpire(String arg0) {
        return 0f;
    }

    @Override
    public CampaignFleetAPI getFleet(String arg0) {
        return (CampaignFleetAPI) map.get(arg0);
    }

    @Override
    public float getFloat(String arg0) {
        Object value = map.get(arg0);
        if (value instanceof Number number) {
            return number.floatValue();
        }
        if (value instanceof String str) {
            return Float.parseFloat(str);
        }
        throw new IllegalStateException("Value for key '" + arg0 + "' is not numeric");
    }

    @Override
    public int getInt(String arg0) {
        Object value = map.get(arg0);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String str) {
            return Integer.parseInt(str);
        }
        throw new IllegalStateException("Value for key '" + arg0 + "' is not numeric");
    }

    @Override
    public Collection<String> getKeys() {
        return Collections.unmodifiableSet(map.keySet());
    }

    @Override
    public long getLong(String arg0) {
        Object value = map.get(arg0);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String str) {
            return Long.parseLong(str);
        }
        throw new IllegalStateException("Value for key '" + arg0 + "' is not numeric");
    }

    @Override
    public Set<String> getRequired(String arg0) {
        return Collections.emptySet();
    }

    @Override
    public String getString(String arg0) {
        Object value = map.get(arg0);
        return value == null ? null : value.toString();
    }

    @Override
    public Vector2f getVector2f(String arg0) {
        return (Vector2f) map.get(arg0);
    }

    @Override
    public boolean is(String arg0, Object arg1) {
        Object value = map.get(arg0);
        return Objects.equals(value, arg1);
    }

    @Override
    public boolean is(String arg0, float arg1) {
        return Float.compare(getFloat(arg0), arg1) == 0;
    }

    @Override
    public boolean is(String arg0, boolean arg1) {
        Object value = map.get(arg0);
        if (value instanceof Boolean bool) {
            return bool == arg1;
        }
        if (value instanceof String str) {
            return Boolean.parseBoolean(str) == arg1;
        }
        throw new IllegalStateException("Value for key '" + arg0 + "' is not boolean");
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public void removeAllRequired(String arg0) {
        // No-op: requirement tracking not needed for this wrapper
    }

    @Override
    public void removeRequired(String arg0, String arg1) {
        // No-op: requirement tracking not needed for this wrapper
    }

    @Override
    public void set(String arg0, Object arg1) {
        map.put(arg0, arg1);
    }

    @Override
    public void set(String arg0, Object arg1, float arg2) {
        map.put(arg0, arg1);
    }

    @Override
    public void unset(String arg0) {
        map.remove(arg0);
    }
    
}
