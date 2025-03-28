/*
 * The MIT License
 *
 * Copyright 2018 The OpenNARS authors.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.opennars.storage;

import org.opennars.entity.Item;

import java.io.Serializable;
import java.util.*;

import org.opennars.inference.BudgetFunctions;
import org.opennars.main.Parameters;

/**
 * Original Bag implementation which distributes items into
 * discrete levels (queues) according to priority
 */
public class Bag<Type extends Item<K>, K> implements Serializable, Iterable<Type> {

    // num of levels for the improved bag and the bag buffer
    // by default the bag buffer has 10 times more levels
    private final int n_working_modes;
    private final int n_levels_IB;
    private final int n_levels_BB;

    // capacity
    private final int capacity_BB;
    private final int capacity_each_level_IB;

    // under this threshold, these levels will be considered dormant
    private final int dormant_level_threshold;

    // the distributors
    // there are 3 type of distributors
    // the "improved_bag_output_distributor" is used to get an item normally as in NARS, though it is different in
    //  putting in new items, now it is not decided just by the budget, but using a bag buffer
    private final Distributor output_distributor_IB;
    private final Distributor[][] input_distributors_IB;
    private final Distributor[][] output_distributors_BB;

    // the item tables
    // for the improved bag, it is a just classical NARS bag with a different way of adding items
    // therefore, it also needs a hash table
    private ArrayList<ArrayList<Type>> item_table_IB;
    private HashMap<K, Type> name_table_IB;
    private HashMap<K, Integer> name_table_idx_IB;
    private int mass_IB;
    private int counter_IB;

    private ArrayList<ArrayList<Type>> item_table_BB;
    private HashMap<K, Type> name_table_BB;

    // indexing & levels
    // for each distributor, it needs an index of the distributor, as well as the level got from the index
    private int output_level_IB;
    private int output_idx_of_level_IB;
    // other distributors are grouped
    private final int[][] input_levels_IB;
    private final int[][] input_idx_of_levels_IB;
    private final int[][] output_levels_BB;
    private final int[][] output_idx_of_levels_BB;

    private int cooldown;

    public Bag(final int levels, final int capacity, Parameters narParameters) {
        this(levels, capacity, (int) (narParameters.BAG_THRESHOLD * levels));
    }

    /**
     * thresholdLevel = 0 disables "fire level completely" threshold effect
     */
    public Bag(final int levels, final int capacity, final int thresholdLevel) {

        // by default, it is 10
        n_working_modes = 5;

        n_levels_IB = levels;
        n_levels_BB = levels * 2;

        capacity_BB = capacity * 10;
        capacity_each_level_IB = capacity;

        dormant_level_threshold = thresholdLevel;

        output_distributor_IB = new Distributor(levels);
        int improved_bag_num_levels_each_mode = n_levels_IB / n_working_modes;
        int bag_buffer_num_levels_each_mode = n_levels_BB / n_working_modes;
        input_distributors_IB = new Distributor[n_working_modes][2];
        output_distributors_BB = new Distributor[n_working_modes][2];
        for (int i = 0; i < n_working_modes; i++) {
            input_distributors_IB[i][0] = new Distributor(i * improved_bag_num_levels_each_mode, (i + 1) * improved_bag_num_levels_each_mode - 1, false);
            input_distributors_IB[i][1] = new Distributor(i * improved_bag_num_levels_each_mode, (i + 1) * improved_bag_num_levels_each_mode - 1, true);
            output_distributors_BB[i][0] = new Distributor(i * bag_buffer_num_levels_each_mode, (i + 1) * bag_buffer_num_levels_each_mode - 1, false);
            output_distributors_BB[i][1] = new Distributor(i * bag_buffer_num_levels_each_mode, (i + 1) * bag_buffer_num_levels_each_mode - 1, true);
        }
        input_levels_IB = new int[n_working_modes][2];
        input_idx_of_levels_IB = new int[n_working_modes][2];
        output_levels_BB = new int[n_working_modes][2];
        output_idx_of_levels_BB = new int[n_working_modes][2];

        cooldown = 0;

        clear();
    }

    public void clear() {

        // clear the improved bag
        item_table_IB = new ArrayList<ArrayList<Type>>(n_levels_IB);
        for (int i = 0; i < n_levels_IB; i++) {
            item_table_IB.add(new ArrayList<Type>());
        }
        name_table_IB = new LinkedHashMap<K, Type>();
        name_table_idx_IB = new LinkedHashMap<K, Integer>();

        // clear the bag buffer
        item_table_BB = new ArrayList<ArrayList<Type>>(n_levels_BB);
        for (int i = 0; i < n_levels_BB; i++) {
            item_table_BB.add(new ArrayList<Type>());
        }
        name_table_BB = new LinkedHashMap<K, Type>();

        // reset the level/index
        // for the output improved bag
        output_idx_of_level_IB = 0;
        output_level_IB = output_distributor_IB.pick(0);
        mass_IB = 0;
        counter_IB = 0;

        for (int i = 0; i < n_working_modes; i++) {
            assert input_idx_of_levels_IB != null;
            input_idx_of_levels_IB[i][0] = 0;
            input_idx_of_levels_IB[i][1] = 0;
            assert input_levels_IB != null;
            assert input_distributors_IB != null;
            input_levels_IB[i][0] = input_distributors_IB[i][0].pick(0);
            input_levels_IB[i][1] = input_distributors_IB[i][1].pick(0);

            assert output_idx_of_levels_BB != null;
            output_idx_of_levels_BB[i][0] = 0;
            output_idx_of_levels_BB[i][1] = 0;
            assert output_levels_BB != null;
            assert output_distributors_BB != null;
            output_levels_BB[i][0] = output_distributors_BB[i][0].pick(0);
            output_levels_BB[i][1] = output_distributors_BB[i][1].pick(0);
        }
    }

    public float getAveragePriority() {
        if (name_table_IB.isEmpty()) {
            return 0.01f;
        }
        float f = (float) mass_IB / (name_table_IB.size() * n_levels_IB);
        if (f > 1) {
            return 1.0f;
        }
        return f;
    }

    public boolean contains(Type it) {
        return name_table_IB.containsValue(it);
    }

    public Type get(K key) {
        return name_table_IB.get(key);
    }

    protected Type put_in_BB(Type newItem) {
        K newKey = newItem.name();
        Type existedItem = name_table_BB.put(newKey, newItem);
        if (existedItem != null) {
            out_of_base_BB(existedItem);
            newItem.merge(existedItem);
        }
        Type overflowItem = into_base_BB(newItem);
        if (overflowItem != null) {
            K overflowKey = overflowItem.name();
            name_table_BB.remove(overflowKey);
            return overflowItem;
        } else {
            return null;
        }
    }

    protected void out_of_base_BB(Type existedItem) {
        float fl = existedItem.getPriority() * n_levels_BB;  // float
        int level = (int) Math.ceil(fl) - 1;
        level = Math.max(level, 0);
        item_table_BB.get(level).remove(existedItem);
    }

    protected boolean empty_level_BB(int level) {
        return item_table_BB.get(level).isEmpty();
    }

    protected Type take_out_first_BB(int level) {
//        System.out.println(1);
//        System.out.println(item_table_BB.get(level).size());
        Type selected = item_table_BB.get(level).get(0);
        item_table_BB.get(level).remove(0);
        return selected;
    }

    protected Type into_base_BB(Type newItem) {
        Type overflow = null;
        float fl = newItem.getPriority() * n_levels_BB;
        int inLevel = (int) Math.ceil(fl) - 1;
        inLevel = Math.max(inLevel, 0);

        if (name_table_BB.size() > capacity_BB) {
            int outLevel = 0;
            while (empty_level_BB(outLevel)) {
                outLevel++;
            }
            if (outLevel > inLevel) {
                return newItem;
            } else {
                overflow = take_out_first_BB(outLevel);
            }
        }
        item_table_BB.get(inLevel).add(newItem);
        return overflow;
    }

    protected void put_in_IB_from_BB() {
        // if there is nothing to put, return
        if (name_table_BB.isEmpty()) {
            return;
        }

        boolean m = true;

        while (m) {

            // deciding the working mode and find the distributors (for receiving of IB and sending of BB)
            int selected_working_mode = (int) (Math.random() * n_working_modes);
            Distributor selected_output_distributor_BB;
            int selected_output_idx_of_levels_BB;
            int selected_output_level_BB;
            Distributor selected_input_distributor_IB;
            int selected_input_idx_of_levels_IB;
            int selected_input_level_IB;

            if (Math.random() < 0.5) {
                selected_output_distributor_BB = output_distributors_BB[selected_working_mode][0];
                selected_output_idx_of_levels_BB = output_idx_of_levels_BB[selected_working_mode][0];
                selected_input_distributor_IB = input_distributors_IB[selected_working_mode][0];
                selected_input_idx_of_levels_IB = input_idx_of_levels_IB[selected_working_mode][0];
            } else {
                selected_output_distributor_BB = output_distributors_BB[selected_working_mode][1];
                selected_output_idx_of_levels_BB = output_idx_of_levels_BB[selected_working_mode][1];
                selected_input_distributor_IB = input_distributors_IB[selected_working_mode][1];
                selected_input_idx_of_levels_IB = input_idx_of_levels_IB[selected_working_mode][1];
            }

            // decide the receiving part
            selected_input_idx_of_levels_IB = selected_input_distributor_IB.next(selected_input_idx_of_levels_IB);
            selected_input_level_IB = selected_input_distributor_IB.pick(selected_input_idx_of_levels_IB);

            // go find a non-empty output BB level
            selected_output_idx_of_levels_BB = selected_output_distributor_BB.next(selected_output_idx_of_levels_BB);
            selected_output_level_BB = selected_output_distributor_BB.pick(selected_output_idx_of_levels_BB);
            int counter = 0;
            if (empty_level_BB(selected_output_level_BB)) {
                while (empty_level_BB(selected_output_level_BB) && counter < selected_output_distributor_BB.order.length) {
                    counter++;
                    selected_output_idx_of_levels_BB = selected_output_distributor_BB.next(selected_output_idx_of_levels_BB);
                    selected_output_level_BB = selected_output_distributor_BB.pick(selected_output_idx_of_levels_BB);
                }
            }
            if (!empty_level_BB(selected_output_level_BB)) {

                m = false;

                Type selected_item = take_out_first_BB(selected_output_level_BB);
                name_table_BB.remove(selected_item.name());

                // put the selected item in IB
                // if the target IB level is full, pop the first one
                if (item_table_IB.get(selected_input_level_IB).size() > capacity_each_level_IB) {
                    Type overflow = take_out_first_IB(selected_input_level_IB);
                    name_table_IB.remove(overflow.name());
                    name_table_idx_IB.remove(overflow.name());
                }
                boolean mark = out_of_base_IB(selected_item);
                if (mark) {
                    name_table_IB.remove(selected_item.name());
                    name_table_idx_IB.remove(selected_item.name());
                }

                item_table_IB.get(selected_input_level_IB).add(selected_item);
                name_table_IB.put(selected_item.name(), selected_item);
                name_table_idx_IB.put(selected_item.name(), selected_input_level_IB);
                mass_IB += (selected_input_level_IB + 1);
            }
        }

    }

    protected Type take_out_first_IB(int level) {
        Type selected = item_table_IB.get(level).get(0);
        item_table_IB.get(level).remove(0);
        mass_IB -= (level + 1);
        return selected;
    }

    public Type putIn(Type newItem) {
        Type overflow = put_in_BB(newItem);
        cooldown++;
        if (cooldown >= 1) {
            put_in_IB_from_BB();
            cooldown = 0;
        }
        return overflow;
    }

    public Type putBack(final Type oldItem, final float forgetCycles, final Memory m) {
        final float relativeThreshold = m.narParameters.FORGET_QUALITY_RELATIVE;
        BudgetFunctions.applyForgetting(oldItem.budget, forgetCycles, relativeThreshold);
        return putIn(oldItem);
    }

    public Type takeOut() {

        if (name_table_IB.isEmpty()) {
            return null;
        }
        if (empty_level_IB(output_level_IB) || (counter_IB == 0)) {
            do {
                output_level_IB = output_distributor_IB.pick(output_idx_of_level_IB);
                output_idx_of_level_IB = output_distributor_IB.next(output_idx_of_level_IB);
            } while (empty_level_IB(output_level_IB));
            if (output_level_IB < dormant_level_threshold) {
                counter_IB = 1;
            } else {
                counter_IB = item_table_IB.get(output_level_IB).size();
            }
        }
        Type selected = take_out_first_IB(output_level_IB);
        name_table_IB.remove(selected.name());
        name_table_idx_IB.remove(selected.name());
        counter_IB--;
        return selected;
    }

    protected boolean empty_level_IB(int level) {
        return item_table_IB.get(level).isEmpty();
    }

    public Type pickOut(K key) {
        Type picked = name_table_IB.get(key);
        if (picked != null) {
            boolean mark = out_of_base_IB(picked);
            if (mark) {
                name_table_IB.remove(key);
                name_table_idx_IB.remove(key);
            }
        }
        return picked;
    }

    public Type pickOut(Type val) {
        return pickOut(val.name());
    }

    protected boolean out_of_base_IB(Type existedItem) {
        if (name_table_idx_IB.containsKey(existedItem.name())) {
            int level = name_table_idx_IB.get(existedItem.name());
            item_table_IB.get(level).remove(existedItem);
            mass_IB -= level + 1;
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder(" ");
        for (int i = n_levels_IB; i >= 0; i--) {
            if (!empty_level_IB(i - 1)) {
                buf.append("\n --- Level ").append(i).append(":\n ");
                for (int j = 0; j < item_table_IB.get(i - 1).size(); j++) {
                    buf.append(item_table_IB.get(i - 1).get(j).toString()).append("\n ");
                }
            }
        }
        return buf.toString();
    }

    /**
     * TODO bad paste from preceding
     */
    public String toStringLong() {
        StringBuilder buf = new StringBuilder(" BAG " + getClass().getSimpleName());
        buf.append(" ").append(showSizes());
        for (int i = n_levels_IB; i >= 0; i--) {
            if (!empty_level_IB(i - 1)) {
                buf.append("\n --- LEVEL ").append(i).append(":\n ");
                for (int j = 0; j < item_table_IB.get(i - 1).size(); j++) {
                    buf.append(item_table_IB.get(i - 1).get(j).toStringLong()).append("\n ");
                }
            }
        }
        buf.append(">>>> end of Bag").append(getClass().getSimpleName());
        return buf.toString();
    }

    String showSizes() {
        StringBuilder buf = new StringBuilder(" ");
        int levels = 0;
        for (ArrayList<Type> items : item_table_IB) {
            if ((items != null) && !items.isEmpty()) {
                levels++;
                buf.append(items.size()).append(" ");
            }
        }
        return "Levels: " + Integer.toString(levels) + ", sizes: " + buf;
    }

    public int size() {

        return name_table_IB.size();
    }

    @Override
    public Iterator<Type> iterator() {
        return name_table_IB.values().iterator();
    }
}
