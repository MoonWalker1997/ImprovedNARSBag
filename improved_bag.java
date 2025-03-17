import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

class Constants {
    public static final int LIMIT = 5000;
}

class ItemEntry {
    public double value;
    public Object item;

    public ItemEntry(double value, Object item) {
        this.value = value;
        this.item = item;
    }
}

class BBuffer {
    private int numBBufferLevels;
    private List<List<ItemEntry>> data;
    private List<List<Integer>> diffusers;
    private int[] cursors;
    private int numWorkingModes;
    private Random random = new Random();

    public BBuffer(int numBBufferLevels, int numWorkingModes) {
        this.numBBufferLevels = numBBufferLevels;
        this.numWorkingModes = numWorkingModes;

        data = new ArrayList<>();
        for (int i = 0; i < numBBufferLevels; i++) {
            data.add(new ArrayList<>());
        }

        diffusers = new ArrayList<>();
        cursors = new int[numWorkingModes];
        int levelsEachMode = numBBufferLevels / numWorkingModes;
        for (int i = 0; i < numWorkingModes; i++) {
            cursors[i] = 0;
            List<Integer> diffuser = new ArrayList<>();
            for (int j = 0; j < levelsEachMode; j++) {
                int k = i * levelsEachMode + j;
                for (int t = 0; t < j + 1; t++) {
                    diffuser.add(k);
                }
            }
            Collections.shuffle(diffuser);
            diffusers.add(diffuser);
        }
    }

    public void addItem(double value, Object item) {
        int lv = (int)(value / (1.0 / numBBufferLevels));
        if (lv >= numBBufferLevels) {
            lv = numBBufferLevels - 1;
        }
        data.get(lv).add(new ItemEntry(value, item));
        if (data.get(lv).size() > Constants.LIMIT * 100) {
            data.get(lv).remove(0);
        }
    }

    public ItemEntry pop(int modeIdx) {
        int count = 1;
        List<Integer> diffuser = diffusers.get(modeIdx);
        cursors[modeIdx] = (cursors[modeIdx] + 1) % diffuser.size();
        while (data.get(diffuser.get(cursors[modeIdx])).isEmpty()) {
            count++;
            if (count > diffuser.size()) {
                return null;
            }
            cursors[modeIdx] = (cursors[modeIdx] + 1) % diffuser.size();
        }
        return data.get(diffuser.get(cursors[modeIdx])).remove(0);
    }
}

class Bag {
    private int numLevels;
    private List<List<ItemEntry>> data;
    private int numWorkingModes;
    private int[] cursorsIn;
    private List<List<Integer>> diffusersIn;
    private int cursorOut;
    private List<Integer> diffuserOut;
    private BBuffer BBuffer;
    private Random random = new Random();

    public Bag(int numLevels, int numBBufferLevels, int numWorkingModes) {
        if (numLevels % numWorkingModes != 0 || numBBufferLevels % numWorkingModes != 0) {
            throw new IllegalArgumentException("numLevels and numBBufferLevels must be divided by numWorkingModes");
        }
        this.numLevels = numLevels;
        this.numWorkingModes = numWorkingModes;

        data = new ArrayList<>();
        for (int i = 0; i < numLevels; i++) {
            data.add(new ArrayList<>());
        }

        cursorsIn = new int[numWorkingModes];
        diffusersIn = new ArrayList<>();
        int levelsEachMode = numLevels / numWorkingModes;
        for (int i = 0; i < numWorkingModes; i++) {
            cursorsIn[i] = 0;
            List<Integer> diffuser = new ArrayList<>();
            for (int j = 0; j < levelsEachMode; j++) {
                int k = i * levelsEachMode + j;
                for (int t = 0; t < j + 1; t++) {
                    diffuser.add(k);
                }
            }
            Collections.shuffle(diffuser);
            diffusersIn.add(diffuser);
        }

        cursorOut = 0;
        diffuserOut = new ArrayList<>();
        for (int i = 0; i < numLevels; i++) {
            for (int t = 0; t < i + 1; t++) {
                diffuserOut.add(i);
            }
        }
        Collections.shuffle(diffuserOut);

        BBuffer = new BBuffer(numBBufferLevels, numWorkingModes);
    }

    public void fromBBufferToBagModeX(int modeIdx) {
        ItemEntry tmp = BBuffer.pop(modeIdx);
        if (tmp != null) {
            List<Integer> diffuser = diffusersIn.get(modeIdx);
            cursorsIn[modeIdx] = (cursorsIn[modeIdx] + 1) % diffuser.size();
            int level = diffuser.get(cursorsIn[modeIdx]);
            data.get(level).add(tmp);
            if (data.get(level).size() > Constants.LIMIT) {
                data.get(level).remove(0);
            }
        }
    }

    public void loadToBBuffer(double value, Object item) {
        BBuffer.addItem(value, item);
    }

    public void fromBBufferToBag() {
        int modeIdx = (int) Math.floor(random.nextDouble() * numWorkingModes);
        fromBBufferToBagModeX(modeIdx);
    }

    public ItemEntry popItem() {
        int count = 1;
        cursorOut = (cursorOut + 1) % diffuserOut.size();
        while (data.get(diffuserOut.get(cursorOut)).isEmpty()) {
            count++;
            if (count > diffuserOut.size()) {
                return null;
            }
            cursorOut = (cursorOut + 1) % diffuserOut.size();
        }
        return data.get(diffuserOut.get(cursorOut)).remove(0);
    }
}
