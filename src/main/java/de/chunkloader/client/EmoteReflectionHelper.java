package de.chunkloader.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
public final class EmoteReflectionHelper {

    private static volatile boolean renderStateReady = false;
    private static Field entityIdField;
    private static Field ageField;

    private static volatile boolean modelPartReady = false;
    private static Method setPivotMethod;
    private static Field pivotXField;
    private static Field pivotYField;
    private static Field pivotZField;

    private EmoteReflectionHelper() {
    }

    public static Integer getEntityId(Object renderState) {
        if (renderState == null) return null;
        ensureRenderStateReflection(renderState);
        if (entityIdField != null) {
            try {
                return entityIdField.getInt(renderState);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public static float getAge(Object renderState) {
        if (renderState == null) return 0.0f;
        ensureRenderStateReflection(renderState);
        if (ageField != null) {
            try {
                return ageField.getFloat(renderState);
            } catch (Exception ignored) {
            }
        }
        return 0.0f;
    }

    public static PlayerEntity resolvePlayer(Object renderState) {
        Integer entityId = getEntityId(renderState);
        if (entityId == null) return null;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) return null;
        Entity entity = client.world.getEntityById(entityId);
        return entity instanceof PlayerEntity p ? p : null;
    }

    public static float getModelPartPosX(ModelPart part) {
        return getModelPartPos(part, 0);
    }

    public static float getModelPartPosY(ModelPart part) {
        return getModelPartPos(part, 1);
    }

    public static float getModelPartPosZ(ModelPart part) {
        return getModelPartPos(part, 2);
    }

    public static void setModelPartPos(ModelPart part, float x, float y, float z) {
        if (part == null) return;
        ensureModelPartReflection(part);

        if (setPivotMethod != null) {
            try {
                setPivotMethod.invoke(part, x, y, z);
                return;
            } catch (Exception ignored) {
            }
        }
        if (pivotXField != null && pivotYField != null && pivotZField != null) {
            try {
                pivotXField.setFloat(part, x);
                pivotYField.setFloat(part, y);
                pivotZField.setFloat(part, z);
                return;
            } catch (Exception ignored) {
            }
        }
    }

    private static float getModelPartPos(ModelPart part, int index) {
        if (part == null) return 0.0f;
        ensureModelPartReflection(part);
        Field[] fields = new Field[] { pivotXField, pivotYField, pivotZField };
        Field f = index >= 0 && index < 3 ? fields[index] : null;
        if (f != null) {
            try {
                return f.getFloat(part);
            } catch (Exception ignored) {
            }
        }
        return 0.0f;
    }

    private static void ensureRenderStateReflection(Object renderState) {
        if (renderStateReady) return;
        synchronized (EmoteReflectionHelper.class) {
            if (renderStateReady) return;

            Class<?> cls = renderState.getClass();
            List<Field> intFields = new ArrayList<>();
            List<Field> floatFields = new ArrayList<>();

            for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    f.setAccessible(true);
                    if (f.getType() == int.class) {
                        intFields.add(f);
                    } else if (f.getType() == float.class) {
                        floatFields.add(f);
                    }
                }
            }

            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.world != null) {
                for (Field f : intFields) {
                    try {
                        int id = f.getInt(renderState);
                        if (client.world.getEntityById(id) != null) {
                            entityIdField = f;
                            break;
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
            if (entityIdField == null && !intFields.isEmpty()) {
                entityIdField = intFields.get(0);
            }

            String[] ageNames = { "age", "ageInTicks", "animationProgress", "tick", "f" };
            for (String name : ageNames) {
                for (Field f : floatFields) {
                    if (f.getName().equals(name)) {
                        ageField = f;
                        break;
                    }
                }
                if (ageField != null) break;
            }
            if (ageField == null) {
                for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
                    String simple = c.getSimpleName();
                    if (simple.contains("EntityRenderState") || simple.contains("RenderState")) {
                        for (Field f : c.getDeclaredFields()) {
                            if (f.getType() == float.class) {
                                f.setAccessible(true);
                                ageField = f;
                                break;
                            }
                        }
                        if (ageField != null) break;
                    }
                }
            }
            if (ageField == null && !floatFields.isEmpty()) {
                ageField = floatFields.get(0);
            }

            renderStateReady = true;
        }
    }

    private static void ensureModelPartReflection(ModelPart part) {
        if (modelPartReady) return;
        synchronized (EmoteReflectionHelper.class) {
            if (modelPartReady) return;

            Class<?> cls = part.getClass();

            for (Method m : cls.getMethods()) {
                if (m.getParameterCount() == 3) {
                    Class<?>[] p = m.getParameterTypes();
                    if (p[0] == float.class && p[1] == float.class && p[2] == float.class) {
                        String name = m.getName().toLowerCase();
                        boolean likelySetter = name.contains("pivot") || name.contains("pos") || name.contains("set")
                            || name.startsWith("m_") || name.startsWith("f_");
                        if (likelySetter || setPivotMethod == null) {
                            try {
                                m.setAccessible(true);
                                setPivotMethod = m;
                                if (likelySetter) break;
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }
            }

            List<Field> floatFields = new ArrayList<>();
            for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (f.getType() == float.class) {
                        f.setAccessible(true);
                        floatFields.add(f);
                    }
                }
            }

            String[] pivotNames = { "pivotX", "pivotY", "pivotZ", "x", "y", "z" };
            Field[] pivotFields = new Field[3];
            int found = 0;
            for (String name : pivotNames) {
                if (found >= 3) break;
                for (Field f : floatFields) {
                    if (f.getName().equals(name)) {
                        int idx = name.contains("X") || name.equals("x") ? 0 : name.contains("Y") || name.equals("y") ? 1 : 2;
                        if (pivotFields[idx] == null) {
                            pivotFields[idx] = f;
                            found++;
                        }
                        break;
                    }
                }
            }
            if (pivotFields[0] != null && pivotFields[1] != null && pivotFields[2] != null) {
                pivotXField = pivotFields[0];
                pivotYField = pivotFields[1];
                pivotZField = pivotFields[2];
            } else if (floatFields.size() >= 3) {
                pivotXField = floatFields.get(0);
                pivotYField = floatFields.get(1);
                pivotZField = floatFields.get(2);
            }

            modelPartReady = true;
        }
    }
}
