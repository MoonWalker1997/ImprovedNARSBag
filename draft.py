import itertools
import random

limit = 50

class BBuffer:

    def __init__(self, num_BBuffer_levels, num_working_modes):
        self.num_BBuffer_levels = num_BBuffer_levels
        self.data = [[] for _ in range(num_BBuffer_levels)]

        self.cursors = []
        self.diffusers = []
        levels_each_mode = num_BBuffer_levels // num_working_modes
        for i in range(num_working_modes):
            self.cursors.append(0)
            self.diffusers.append(
                list(
                    itertools.chain(
                        *[[k for _ in range(j + 1)]
                          for j, k in enumerate(range(i * levels_each_mode, (i+1) * levels_each_mode))])))
            random.shuffle(self.diffusers[-1])

    def add_item(self, value, item):
        lv = int(value // (1 / self.num_BBuffer_levels))
        self.data[lv].append((value, item))
        if len(self.data[lv]) > limit * 100:
            self.data[lv].pop(0)

    def pop(self, mode_idx):
        count = 1
        self.cursors[mode_idx] = (self.cursors[mode_idx] + 1) % len(self.diffusers[mode_idx])
        while not self.data[int(self.diffusers[mode_idx][self.cursors[mode_idx]])]:
            count += 1
            if count > len(self.diffusers[mode_idx]):
                return None
            self.cursors[mode_idx] = (self.cursors[mode_idx] + 1) % len(self.diffusers[mode_idx])
        return self.data[int(self.diffusers[mode_idx][self.cursors[mode_idx]])].pop(0)

b = BBuffer(10, 5)
b.add_item(0.11, 0)
b.add_item(0.05, 1)

print(b.pop(0))