import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class BagSystem {

    public static final int LIMIT = 5000;

    public static class Pair {
        public double value;
        public Object item;

        public Pair(double value, Object item) {
            this.value = value;
            this.item = item;
        }
    }

    public static class BBuffer {
        private int numBBufferLevels;
        private List<List<Pair>> data;
        private List<Integer> cursors;
        private List<List<Integer>> diffusers;
        private Random random = new Random();

        public BBuffer(int numBBufferLevels, int numWorkingModes) {
            this.numBBufferLevels = numBBufferLevels;
            data = new ArrayList<>();
            for (int i = 0; i < numBBufferLevels; i++) {
                data.add(new ArrayList<>());
            }

            cursors = new ArrayList<>();
            diffusers = new ArrayList<>();

            int levelsEachMode = numBBufferLevels / numWorkingModes;
            for (int i = 0; i < numWorkingModes; i++) {
                cursors.add(0);
                List<Integer> diffuser = new ArrayList<>();
                int j = 0;
                for (int k = i * levelsEachMode; k < (i + 1) * levelsEachMode; k++) {
                    for (int rep = 0; rep < (j + 1); rep++) {
                        diffuser.add(k);
                    }
                    j++;
                }
                Collections.shuffle(diffuser, random);
                diffusers.add(diffuser);
            }
        }

        public void addItem(double value, Object item) {
            int lv = (int) (value / (1.0 / numBBufferLevels));
            if (lv >= numBBufferLevels) {
                lv = numBBufferLevels - 1;
            }
            data.get(lv).add(new Pair(value, item));
            if (data.get(lv).size() > LIMIT * 100) {
                data.get(lv).remove(0);
            }
        }

        public Pair pop(int modeIdx) {
            int count = 1;
            List<Integer> diffuser = diffusers.get(modeIdx);
            int cursor = (cursors.get(modeIdx) + 1) % diffuser.size();
            cursors.set(modeIdx, cursor);

            while (data.get(diffuser.get(cursor)).isEmpty()) {
                count++;
                if (count > diffuser.size()) {
                    return null;
                }
                cursor = (cursor + 1) % diffuser.size();
                cursors.set(modeIdx, cursor);
            }
            return data.get(diffuser.get(cursor)).remove(0);
        }
    }

    public static class Bag {
        private int numLevels;
        private List<List<Pair>> data;
        private int numWorkingModes;

        private List<Integer> cursorsIn;
        private List<List<Integer>> diffusersIn;

        private int cursorOut;
        private List<Integer> diffuserOut;

        private BBuffer bBuffer;
        private Random random = new Random();

        public Bag(int numLevels, int numBBufferLevels, int numWorkingModes) {
            if (numLevels % numWorkingModes != 0 || numBBufferLevels % numWorkingModes != 0) {
                throw new IllegalArgumentException("numLevels and numBBufferLevels must be divided by numWorkingModes");
            }
            this.numLevels = numLevels;
            data = new ArrayList<>();
            for (int i = 0; i < numLevels; i++) {
                data.add(new ArrayList<>());
            }
            this.numWorkingModes = numWorkingModes;

            cursorsIn = new ArrayList<>();
            diffusersIn = new ArrayList<>();
            int levelsEachMode = numLevels / numWorkingModes;
            for (int i = 0; i < numWorkingModes; i++) {
                cursorsIn.add(0);
                List<Integer> diffuser = new ArrayList<>();
                int j = 0;
                for (int k = i * levelsEachMode; k < (i + 1) * levelsEachMode; k++) {
                    for (int rep = 0; rep < (j + 1); rep++) {
                        diffuser.add(k);
                    }
                    j++;
                }
                Collections.shuffle(diffuser, random);
                diffusersIn.add(diffuser);
            }

            cursorOut = 0;
            diffuserOut = new ArrayList<>();
            for (int i = 0; i < numLevels; i++) {
                for (int rep = 0; rep < i + 1; rep++) {
                    diffuserOut.add(i);
                }
            }
            Collections.shuffle(diffuserOut, random);

            bBuffer = new BBuffer(numBBufferLevels, numWorkingModes);
        }

        public void fromBBufferToBagModeX(int modeIdx) {
            Pair tmp = bBuffer.pop(modeIdx);
            if (tmp != null) {
                List<Integer> diffuser = diffusersIn.get(modeIdx);
                int cursor = (cursorsIn.get(modeIdx) + 1) % diffuser.size();
                cursorsIn.set(modeIdx, cursor);
                int index = diffuser.get(cursor);
                data.get(index).add(tmp);
                if (data.get(index).size() > LIMIT) {
                    data.get(index).remove(0);
                }
            }
        }

        public void loadToBBuffer(double value, Object item) {
            bBuffer.addItem(value, item);
        }

        public void fromBBufferToBag() {
            int modeIdx = (int) Math.floor(random.nextDouble() * numWorkingModes);
            fromBBufferToBagModeX(modeIdx);
        }

        public Pair popItem() {
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

    // testing (optional)
    public static void main(String[] args) {
        int numLevels = 10;
        int numBBufferLevels = 10;
        int numWorkingModes = 2;
        Bag bag = new Bag(numLevels, numBBufferLevels, numWorkingModes);

        for (int i = 0; i < 20; i++) {
            double value = Math.random();
            bag.loadToBBuffer(value, "Item " + i);
        }

        for (int i = 0; i < 20; i++) {
            bag.fromBBufferToBag();
        }

        Pair p = bag.popItem();
        if (p != null) {
            System.out.println("Popped: value=" + p.value + ", item=" + p.item);
        } else {
            System.out.println("No item popped.");
        }
    }
}
