import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

class Pair<F, S> {
    private F first;
    private S second;

    public Pair(F first, S second) {
        this.first = first;
        this.second = second;
    }

    public F getFirst() {
        return first;
    }

    public S getSecond() {
        return second;
    }

    public void setFirst(F first) {
        this.first = first;
    }

    public void setSecond(S second) {
        this.second = second;
    }
}

class Type {
    private String key;
    private float priority;

    public Type(String key, float priority) {
        this.key = key;
        this.priority = priority;
    }

    public String getKey() {
        return key;
    }

    public float getPriority() {
        return priority;
    }

    @Override
    public String toString() {
        return "Type{" + "key='" + key + '\'' + ", priority=" + priority + '}';
    }
}

class BBuffer {
    private int numBBufferLevels;
    private List<List<Pair<Float, Type>>> data;
    private List<Integer> cursorsL;
    private List<List<Integer>> diffusersL;
    private List<Integer> cursorsS;
    private List<List<Integer>> diffusersS;

    private static final int LIMIT = Bag.LIMIT;

    private Random random;

    public BBuffer(int numBBufferLevels, int numWorkingModes) {
        this.numBBufferLevels = numBBufferLevels;
        data = new ArrayList<>();
        for (int i = 0; i < numBBufferLevels; i++) {
            data.add(new ArrayList<>());
        }

        int levelsEachMode = numBBufferLevels / numWorkingModes;
        random = new Random();

        cursorsL = new ArrayList<>();
        diffusersL = new ArrayList<>();
        for (int i = 0; i < numWorkingModes; i++) {
            cursorsL.add(0);
            List<Integer> diffuser = new ArrayList<>();
            for (int j = 0; j < levelsEachMode; j++) {
                int k = i * levelsEachMode + j;
                for (int count = 0; count < j + 1; count++) {
                    diffuser.add(k);
                }
            }
            Collections.shuffle(diffuser, random);
            diffusersL.add(diffuser);
        }

        cursorsS = new ArrayList<>();
        diffusersS = new ArrayList<>();
        for (int i = 0; i < numWorkingModes; i++) {
            cursorsS.add(0);
            List<Integer> diffuser = new ArrayList<>();
            for (int j = 0; j < levelsEachMode; j++) {
                int k = i * levelsEachMode + j;
                for (int count = 0; count < levelsEachMode - j; count++) {
                    diffuser.add(k);
                }
            }
            Collections.shuffle(diffuser, random);
            diffusersS.add(diffuser);
        }
    }

    public void clear() {
        data = new ArrayList<>();
        for (int i = 0; i < numBBufferLevels; i++) {
            data.add(new ArrayList<>());
        }
    }

    public void addItem(float value, Type type) {
        int lv = (int)(value / (1.0 / numBBufferLevels));
        if (lv < 0) {
            lv = 0;
        }
        if (lv >= numBBufferLevels) {
            lv = numBBufferLevels - 1;
        }
        data.get(lv).add(new Pair<>(value, type));
        if (data.get(lv).size() > LIMIT * 100) {
            data.get(lv).remove(0);
        }
    }

    public Pair<Float, Type> popL(int modeIdx) {
        int count = 1;
        int cursor = (cursorsL.get(modeIdx) + 1) % diffusersL.get(modeIdx).size();
        cursorsL.set(modeIdx, cursor);
        while (data.get(diffusersL.get(modeIdx).get(cursor)).isEmpty()) {
            count++;
            if (count > diffusersL.get(modeIdx).size()) {
                return new Pair<>(0f, null);
            }
            cursor = (cursor + 1) % diffusersL.get(modeIdx).size();
            cursorsL.set(modeIdx, cursor);
        }
        return data.get(diffusersL.get(modeIdx).get(cursor)).remove(0);
    }

    public Pair<Float, Type> popS(int modeIdx) {
        int count = 1;
        int cursor = (cursorsS.get(modeIdx) + 1) % diffusersS.get(modeIdx).size();
        cursorsS.set(modeIdx, cursor);
        while (data.get(diffusersS.get(modeIdx).get(cursor)).isEmpty()) {
            count++;
            if (count > diffusersS.get(modeIdx).size()) {
                return new Pair<>(0f, null);
            }
            cursor = (cursor + 1) % diffusersS.get(modeIdx).size();
            cursorsS.set(modeIdx, cursor);
        }
        return data.get(diffusersS.get(modeIdx).get(cursor)).remove(0);
    }
}

class Bag {
    public static final int LIMIT = 100;

    private int numLevels;
    private int numWorkingModes;
    private int popFrequency;
    private int popCounter;

    private List<List<Pair<Float, Type>>> data;

    private List<Integer> cursorsInL;
    private List<List<Integer>> diffusersInL;

    private List<Integer> cursorsInS;
    private List<List<Integer>> diffusersInS;

    private int cursorOut;
    private List<Integer> diffuserOut;

    private BBuffer bBuffer;

    private Random random;

    public Bag(int numLevels, int numBBufferLevels, int numWorkingModes, int popFrequency) {
        if (numLevels % numWorkingModes != 0 || numBBufferLevels % numWorkingModes != 0) {
            throw new IllegalArgumentException("numLevels and numBBufferLevels must be divisible by numWorkingModes");
        }
        this.numLevels = numLevels;
        this.numWorkingModes = numWorkingModes;
        this.popFrequency = popFrequency;
        this.popCounter = 0;
        random = new Random();

        data = new ArrayList<>();
        for (int i = 0; i < numLevels; i++) {
            data.add(new ArrayList<>());
        }

        cursorsInL = new ArrayList<>();
        diffusersInL = new ArrayList<>();
        int levelsEachMode = numLevels / numWorkingModes;
        for (int i = 0; i < numWorkingModes; i++) {
            cursorsInL.add(0);
            List<Integer> diffuser = new ArrayList<>();
            for (int j = 0; j < levelsEachMode; j++) {
                int k = i * levelsEachMode + j;
                for (int count = 0; count < j + 1; count++) {
                    diffuser.add(k);
                }
            }
            Collections.shuffle(diffuser, random);
            diffusersInL.add(diffuser);
        }

        cursorsInS = new ArrayList<>();
        diffusersInS = new ArrayList<>();
        for (int i = 0; i < numWorkingModes; i++) {
            cursorsInS.add(0);
            List<Integer> diffuser = new ArrayList<>();
            for (int j = 0; j < levelsEachMode; j++) {
                int k = i * levelsEachMode + j;
                for (int count = 0; count < levelsEachMode - j; count++) {
                    diffuser.add(k);
                }
            }
            Collections.shuffle(diffuser, random);
            diffusersInS.add(diffuser);
        }

        cursorOut = 0;
        diffuserOut = new ArrayList<>();
        for (int i = 0; i < numLevels; i++) {
            for (int j = 0; j < i + 1; j++) {
                diffuserOut.add(i);
            }
        }
        Collections.shuffle(diffuserOut, random);

        bBuffer = new BBuffer(numBBufferLevels, numWorkingModes);
    }

    public void clear() {
        data = new ArrayList<>();
        for (int i = 0; i < numLevels; i++) {
            data.add(new ArrayList<>());
        }
        bBuffer.clear();
    }

    public float getAveragePriority() {
        float sum = 0;
        int count = 0;
        for (List<Pair<Float, Type>> bucket : data) {
            for (Pair<Float, Type> task : bucket) {
                sum += task.getFirst();
                count++;
            }
        }
        return count == 0 ? 0.01f : sum / count;
    }

    public boolean contains(Type type) {
        for (List<Pair<Float, Type>> bucket : data) {
            for (Pair<Float, Type> task : bucket) {
                if (type.getKey().equals(task.getSecond().getKey())) {
                    return true;
                }
            }
        }
        return false;
    }

    public Type get(Object key) {
        for (List<Pair<Float, Type>> bucket : data) {
            for (Pair<Float, Type> task : bucket) {
                if (key.equals(task.getSecond().getKey())) {
                    return task.getSecond();
                }
            }
        }
        return null;
    }

    public void putIn(Type type) {
        bBuffer.addItem(type.getPriority(), type);
        if (popCounter == popFrequency) {
            fromBBufferToBag();
            popCounter = 0;
        }
        popCounter++;
    }

    public void putBack(Type oldType, int forgetCycles, int m) {
        TBD
    }

    public void fromBBufferToBagModeX(int modeIdx) {
        if (random.nextDouble() < 0.5) {
            Pair<Float, Type> pair = bBuffer.popL(modeIdx);
            if (pair.getSecond() != null) {
                int newCursor = (cursorsInL.get(modeIdx) + 1) % diffusersInL.get(modeIdx).size();
                cursorsInL.set(modeIdx, newCursor);
                int index = diffusersInL.get(modeIdx).get(newCursor);
                data.get(index).add(pair.getSecond());
                if (data.get(index).size() > LIMIT) {
                    data.get(index).remove(0);
                }
            }
        } else {
            Pair<Float, Type> pair = bBuffer.popS(modeIdx);
            if (pair.getSecond() != null) {
                int newCursor = (cursorsInS.get(modeIdx) + 1) % diffusersInS.get(modeIdx).size();
                cursorsInS.set(modeIdx, newCursor);
                int index = diffusersInS.get(modeIdx).get(newCursor);
                data.get(index).add(pair.getSecond());
                if (data.get(index).size() > LIMIT) {
                    data.get(index).remove(0);
                }
            }
        }
    }

    public void fromBBufferToBag() {
        int modeIdx = random.nextInt(numWorkingModes);
        fromBBufferToBagModeX(modeIdx);
    }

    public Type takeOut() {
        int count = 1;
        cursorOut = (cursorOut + 1) % diffuserOut.size();
        while (data.get(diffuserOut.get(cursorOut)).isEmpty()) {
            count++;
            if (count > diffuserOut.size()) {
                return null;
            }
            cursorOut = (cursorOut + 1) % diffuserOut.size();
        }
        Pair<Float, Type> pair = data.get(diffuserOut.get(cursorOut)).remove(0);
        return pair.getSecond();
    }

    public Type pickOut(Object key) {
        for (int i = 0; i < data.size(); i++) {
            List<Pair<Float, Type>> bucket = data.get(i);
            for (int j = 0; j < bucket.size(); j++) {
                Pair<Float, Type> task = bucket.get(j);
                if (key.equals(task.getSecond().getKey())) {
                    bucket.remove(j);
                    return task.getSecond();
                }
            }
        }
        return null;
    }
}
