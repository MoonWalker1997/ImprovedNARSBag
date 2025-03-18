import itertools
import math
import random
from typing import Union, Tuple

import numpy as np
from matplotlib import pyplot as plt

from utils import ChaoticNumberGenerator

limit: int = 5000


class Item:

    def __init__(self, key, priority):
        self.key = key
        self.priority = priority


class BBuffer:

    def __init__(self, num_BBuffer_levels: int, num_working_modes: int):
        self.num_BBuffer_levels: int = num_BBuffer_levels
        self.data: list[list[tuple[float, Item], ...], ...] = [[] for _ in range(num_BBuffer_levels)]

        levels_each_mode: int = num_BBuffer_levels // num_working_modes

        self.cursors_l: list[int, ...] = []
        self.diffusers_l: list[list[int, ...], ...] = []
        for i in range(num_working_modes):
            self.cursors_l.append(0)
            self.diffusers_l.append(
                list(
                    itertools.chain(
                        *[[k for _ in range(j + 1)]
                          for j, k in enumerate(range(i * levels_each_mode, (i+1) * levels_each_mode))])))
            random.shuffle(self.diffusers_l[-1])

        self.cursors_s: list[int, ...] = []
        self.diffusers_s: list[list[int, ...], ...] = []
        for i in range(num_working_modes):
            self.cursors_s.append(0)
            self.diffusers_s.append(
                list(
                    itertools.chain(
                        *[[k for _ in range(levels_each_mode - j)]
                          for j, k in enumerate(range(i * levels_each_mode, (i + 1) * levels_each_mode))])))
            random.shuffle(self.diffusers_s[-1])

    def clear(self):
        self.data: list[list[list[float, Item], ...], ...] = [[] for _ in range(self.num_BBuffer_levels)]

    def add_item(self, value: float, item: Item) -> None:
        lv: int = int(value // (1 / self.num_BBuffer_levels))
        self.data[lv].append((value, item))
        if len(self.data[lv]) > limit * 100:  # the limit of bbuffers is 100 times of the limit in bags
            self.data[lv].pop(0)

    def pop_l(self, mode_idx: int) -> Union[tuple[float, None], tuple[float, Item]]:
        count: int = 1
        self.cursors_l[mode_idx] = (self.cursors_l[mode_idx] + 1) % len(self.diffusers_l[mode_idx])
        while not self.data[int(self.diffusers_l[mode_idx][self.cursors_l[mode_idx]])]:
            count += 1
            if count > len(self.diffusers_l[mode_idx]):
                return 0., None
            self.cursors_l[mode_idx] = (self.cursors_l[mode_idx] + 1) % len(self.diffusers_l[mode_idx])
        return self.data[int(self.diffusers_l[mode_idx][self.cursors_l[mode_idx]])].pop(0)

    def pop_s(self, mode_idx: int) -> Union[tuple[float, None], tuple[float, Item]]:
        count: int = 1
        self.cursors_s[mode_idx] = (self.cursors_s[mode_idx] + 1) % len(self.diffusers_s[mode_idx])
        while not self.data[int(self.diffusers_s[mode_idx][self.cursors_s[mode_idx]])]:
            count += 1
            if count > len(self.diffusers_s[mode_idx]):
                return 0., None
            self.cursors_s[mode_idx] = (self.cursors_s[mode_idx] + 1) % len(self.diffusers_s[mode_idx])
        return self.data[int(self.diffusers_s[mode_idx][self.cursors_s[mode_idx]])].pop(0)


class Bag:

    def __init__(self, num_levels: int, num_BBuffer_levels: int, num_working_modes: int, pop_frequency: int):

        assert (num_levels // num_working_modes == num_levels / num_working_modes
                and num_BBuffer_levels // num_working_modes == num_BBuffer_levels / num_working_modes)

        self.num_levels: int = num_levels
        self.num_working_modes: int = num_working_modes
        self.pop_frequency: int = pop_frequency
        self.pop_counter: int = 0

        self.data: list[list[tuple[float, Item], ...], ...] = [[] for _ in range(num_levels)]

        self.cursors_in_l: list[int, ...] = []
        self.diffusers_in_l: list[list[int, ...], ...] = []
        levels_each_mode: int = num_levels // num_working_modes
        for i in range(num_working_modes):
            self.cursors_in_l.append(0)
            self.diffusers_in_l.append(
                list(
                    itertools.chain(
                        *[[k for _ in range(j + 1)]
                          for j, k in enumerate(range(i * levels_each_mode, (i + 1) * levels_each_mode))])))
            random.shuffle(self.diffusers_in_l[-1])

        self.cursors_in_s: list[int, ...] = []
        self.diffusers_in_s: list[list[int, ...], ...] = []
        levels_each_mode: int = num_levels // num_working_modes
        for i in range(num_working_modes):
            self.cursors_in_s.append(0)
            self.diffusers_in_s.append(
                list(
                    itertools.chain(
                        *[[k for _ in range(levels_each_mode - j)]
                          for j, k in enumerate(range(i * levels_each_mode, (i + 1) * levels_each_mode))])))
            random.shuffle(self.diffusers_in_s[-1])

        self.cursor_out: int = 0
        self.diffuser_out: list[int, ...] = (
            list(itertools.chain(*[[i for _ in range(i + 1)] for i in range(num_levels)])))
        random.shuffle(self.diffuser_out)

        self.BBuffer: BBuffer = BBuffer(num_BBuffer_levels, num_working_modes)

    def clear(self) -> None:
        self.data: list[list[list[float, Item], ...], ...] = [[] for _ in range(self.num_levels)]
        self.BBuffer.clear()

    def getAveragePriority(self) -> float:
        tmp: float = 0
        counter: int = 0
        for each_bucket in self.data:
            for each_task in each_bucket:
                tmp += each_task[0]
                counter += 1
        if counter == 0:
            return 0.01
        else:
            return tmp / counter

    def contains(self, item: Item) -> bool:
        for each_bucket in self.data:
            for each_task in each_bucket:
                if item.key == each_task[1].key:
                    return True
        return False

    def get(self, key) -> Union[Item, None]:
        for each_bucket in self.data:
            for each_task in each_bucket:
                if key == each_task[1].key:
                    return each_task[1]
        return None

    def putIn(self, item: Item) -> None:
        self.BBuffer.add_item(item.priority, item)
        if self.pop_counter == self.pop_frequency:
            self.from_BBuffer_to_Bag()
            self.pop_counter = 0
        self.pop_counter += 1

    def putBack(self, old_item, forget_cycles, m):
        pass

    def from_BBuffer_to_Bag_mode_x(self, mode_idx: int) -> None:
        if random.random() < 0.5:
            p, tmp = self.BBuffer.pop_l(mode_idx)
            if tmp is not None:
                self.cursors_in_l[mode_idx] = (self.cursors_in_l[mode_idx] + 1) % len(self.diffusers_in_l[mode_idx])
                self.data[int(self.diffusers_in_l[mode_idx][self.cursors_in_l[mode_idx]])].append(tmp)
                if len(self.data[int(self.diffusers_in_l[mode_idx][self.cursors_in_l[mode_idx]])]) > limit:
                    self.data[int(self.diffusers_in_l[mode_idx][self.cursors_in_l[mode_idx]])].pop(0)
        else:
            p, tmp = self.BBuffer.pop_s(mode_idx)
            if tmp is not None:
                self.cursors_in_s[mode_idx] = (self.cursors_in_s[mode_idx] + 1) % len(self.diffusers_in_s[mode_idx])
                self.data[int(self.diffusers_in_s[mode_idx][self.cursors_in_s[mode_idx]])].append(tmp)
                if len(self.data[int(self.diffusers_in_s[mode_idx][self.cursors_in_s[mode_idx]])]) > limit:
                    self.data[int(self.diffusers_in_s[mode_idx][self.cursors_in_s[mode_idx]])].pop(0)

    def from_BBuffer_to_Bag(self) -> None:
        self.from_BBuffer_to_Bag_mode_x(math.floor(random.random() * self.num_working_modes))

    def takeOut(self) -> Union[Item, None]:
        count: int = 1
        self.cursor_out = (self.cursor_out + 1) % len(self.diffuser_out)
        while not self.data[self.diffuser_out[self.cursor_out]]:
            count += 1
            if count > len(self.diffuser_out):
                return None
            self.cursor_out = (self.cursor_out + 1) % len(self.diffuser_out)
        p, ret = self.data[self.diffuser_out[self.cursor_out]].pop(0)
        return ret

    def pickOut(self, key) -> Union[Item, None]:
        for i, each_bucket in enumerate(self.data):
            for j, each_task in enumerate(each_bucket):
                if key == each_task[1].key:
                    return self.data[i].pop(j)[1]
        return None


if __name__ == "__main__":

    record = []

    g = ChaoticNumberGenerator(0.05)
    numbers = g.generate_numbers(50000)

    b = Bag(50, 1000, 10)

    while len(numbers) > 10:
        # 10 in 1 out
        for _ in range(10):
            b.load_to_BBuffer(numbers.pop(), Item(random.random()))
        b.from_BBuffer_to_Bag()

    b.contains(Item(random.random()))

    plt.figure(figsize=(10, 4))
    plt.grid()
    tmp = [np.average([each[0] for each in b.data[i]]) for i in range(len(b.data))]
    plt.plot(tmp)
    plt.xlabel("buckets")
    plt.ylabel("average priority")
    plt.show()

    plt.figure(figsize=(10, 4))
    plt.grid()
    plt.plot([len(b.data[i]) for i in range(b.num_levels)])
    plt.xlabel("buckets")
    plt.ylabel("num of tasks")
    plt.show()
