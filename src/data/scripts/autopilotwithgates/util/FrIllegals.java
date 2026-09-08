package data.scripts.autopilotwithgates.util;

import static com.genir.renderer.agent.IllegalTransformations.transformations;

public class FrIllegals {
    public static String transform(String name) {
        return transformations.getOrDefault(name, name);
    }

    public static String[] transform(String[] names) {
        String[] transformed = new String[names.length];

        for (int i = 0; i < names.length; i++) {
            transformed[i] = transform(names[i]);
        }

        return transformed;
    }
}