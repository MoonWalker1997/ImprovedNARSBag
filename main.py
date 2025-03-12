import itertools
import random

import numpy as np
from matplotlib import pyplot as plt

from utils import ChaoticNumberGenerator

limit = 50


class BBuffer:

    def __init__(self, num_BBuffer_levels):
        self.num_BBuffer_levels = num_BBuffer_levels
        self.data = [[] for _ in range(num_BBuffer_levels)]

        self.cursor_l = 0
        self.diffusor_l = list(itertools.chain(*[[i for _ in range(i + 1)] for i in range(num_BBuffer_levels)]))
        random.shuffle(self.diffusor_l)

        self.cursor_s = 0
        self.diffusor_s = list(
            itertools.chain(*[[i for _ in range(num_BBuffer_levels - i + 1)] for i in range(num_BBuffer_levels)]))
        random.shuffle(self.diffusor_s)

        self.hist_l = []
        self.hist_s = []

    def add_item(self, value, item):
        lv = int(value // (1 / self.num_BBuffer_levels))
        self.data[lv].append((value, item))
        if len(self.data[lv]) > limit * 100:
            self.data[lv].pop(0)

    def pop_l(self):
        count = 1
        self.cursor_l = (self.cursor_l + 1) % len(self.diffusor_l)
        while not self.data[self.diffusor_l[self.cursor_l]]:
            count += 1
            if count > len(self.diffusor_l):
                return None
            self.cursor_l = (self.cursor_l + 1) % len(self.diffusor_l)
        self.hist_l.append(self.diffusor_l[self.cursor_l])
        return self.data[self.diffusor_l[self.cursor_l]].pop(0)

    def pop_s(self):
        count = 1
        self.cursor_s = (self.cursor_s + 1) % len(self.diffusor_s)
        while not self.data[self.diffusor_s[self.cursor_s]]:
            count += 1
            if count > len(self.diffusor_s):
                return None
            self.cursor_s = (self.cursor_s + 1) % len(self.diffusor_s)
        self.hist_s.append(self.diffusor_s[self.cursor_s])
        return self.data[self.diffusor_s[self.cursor_s]].pop(0)


class Bag:

    def __init__(self, num_levels, num_BBuffer_levels):
        self.num_levels = num_levels
        self.data = [[] for _ in range(num_levels)]

        self.cursor_in_l = 0
        self.diffusor_in_l = list(itertools.chain(*[[i for _ in range(i + 1)] for i in range(num_levels)]))
        random.shuffle(self.diffusor_in_l)

        self.cursor_in_s = 0
        self.diffusor_in_s = list(itertools.chain(*[[i for _ in range(num_levels - i)] for i in range(num_levels)]))
        random.shuffle(self.diffusor_in_s)

        self.cursor_out = 0
        self.diffusor_out = list(itertools.chain(*[[i for _ in range(i + 1)] for i in range(num_levels)]))
        random.shuffle(self.diffusor_out)

        self.BBuffer = BBuffer(num_BBuffer_levels)

        self.E_1 = []
        self.E_2 = []

    def add_item_l(self):
        tmp = self.BBuffer.pop_l()
        if tmp is not None:
            self.cursor_in_l = (self.cursor_in_l + 1) % len(self.diffusor_in_l)
            self.data[self.diffusor_in_l[self.cursor_in_l]].append(tmp)
            if len(self.data[self.diffusor_in_l[self.cursor_in_l]]) > limit:
                self.data[self.diffusor_in_l[self.cursor_in_l]].pop(0)

    def add_item_s(self):
        tmp = self.BBuffer.pop_s()
        if tmp is not None:
            self.cursor_in_s = (self.cursor_in_s + 1) % len(self.diffusor_in_s)
            self.data[self.diffusor_in_s[self.cursor_in_s]].append(tmp)
            if len(self.data[self.diffusor_in_s[self.cursor_in_s]]) > limit:
                self.data[self.diffusor_in_s[self.cursor_in_s]].pop(0)

    def add_item_preload(self, value, item):
        self.BBuffer.add_item(value, item)

    def add_item_fire(self):
        if random.random() < 0.5:
            self.add_item_s()
        else:
            self.add_item_l()

    def pop_item(self):
        count = 1
        self.cursor_out = (self.cursor_out + 1) % len(self.diffusor_out)
        while not self.data[self.diffusor_out[self.cursor_out]]:
            count += 1
            if count > len(self.diffusor_out):
                return None
            self.cursor_out = (self.cursor_out + 1) % len(self.diffusor_out)
        ret = self.data[self.diffusor_out[self.cursor_out]].pop(0)
        return ret


if __name__ == "__main__":

    record = []

    g = ChaoticNumberGenerator(0.05)
    numbers = g.generate_numbers(50000)

    b = Bag(100, 500)

    while len(numbers) > 10:
        for _ in range(10):
            b.add_item_preload(numbers.pop(), random.random())
        b.add_item_fire()

    plt.figure(figsize=(10, 4))
    plt.grid()
    tmp = [np.average([each[0] for each in b.data[i]]) for i in range(len(b.data))]
    plt.plot(tmp)
    plt.xlabel("levels")
    plt.ylabel("average priority")
    plt.show()

    plt.figure(figsize=(10, 4))
    plt.grid()
    plt.plot([len(b.data[i]) for i in range(100)])
    plt.show()
