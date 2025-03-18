import itertools
import math
import random

import numpy as np
from matplotlib import pyplot as plt

from utils import ChaoticNumberGenerator

limit: int = 5000


class BBuffer:

    def __init__(self, num_BBuffer_levels: int, num_working_modes: int):
        self.num_BBuffer_levels: int = num_BBuffer_levels
        self.data: list[list[list[float, any], ...], ...] = [[] for _ in range(num_BBuffer_levels)]

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

    def add_item(self, value: float, item: any) -> None:
        lv: int = int(value // (1 / self.num_BBuffer_levels))
        self.data[lv].append([value, item])
        if len(self.data[lv]) > limit * 100:  # the limit of bbuffers is 100 times of the limit in bags
            self.data[lv].pop(0)

    def pop_l(self, mode_idx: int) -> any:
        count: int = 1
        self.cursors_l[mode_idx] = (self.cursors_l[mode_idx] + 1) % len(self.diffusers_l[mode_idx])
        while not self.data[int(self.diffusers_l[mode_idx][self.cursors_l[mode_idx]])]:
            count += 1
            if count > len(self.diffusers_l[mode_idx]):
                return
            self.cursors_l[mode_idx] = (self.cursors_l[mode_idx] + 1) % len(self.diffusers_l[mode_idx])
        return self.data[int(self.diffusers_l[mode_idx][self.cursors_l[mode_idx]])].pop(0)

    def pop_s(self, mode_idx: int) -> any:
        count: int = 1
        self.cursors_s[mode_idx] = (self.cursors_s[mode_idx] + 1) % len(self.diffusers_s[mode_idx])
        while not self.data[int(self.diffusers_s[mode_idx][self.cursors_s[mode_idx]])]:
            count += 1
            if count > len(self.diffusers_s[mode_idx]):
                return
            self.cursors_s[mode_idx] = (self.cursors_s[mode_idx] + 1) % len(self.diffusers_s[mode_idx])
        return self.data[int(self.diffusers_s[mode_idx][self.cursors_s[mode_idx]])].pop(0)


class Bag:

    def __init__(self, num_levels: int, num_BBuffer_levels: int, num_working_modes: int):

        assert (num_levels // num_working_modes == num_levels / num_working_modes
                and num_BBuffer_levels // num_working_modes == num_BBuffer_levels / num_working_modes)

        self.num_levels: int = num_levels
        self.data: list[list[list[float, any], ...], ...] = [[] for _ in range(num_levels)]
        self.num_working_modes: int = num_working_modes

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

    def from_BBuffer_to_Bag_mode_x(self, mode_idx: int) -> None:
        if random.random() < 0.5:
            tmp: any = self.BBuffer.pop_l(mode_idx)
            if tmp is not None:
                self.cursors_in_l[mode_idx] = (self.cursors_in_l[mode_idx] + 1) % len(self.diffusers_in_l[mode_idx])
                self.data[int(self.diffusers_in_l[mode_idx][self.cursors_in_l[mode_idx]])].append(tmp)
                if len(self.data[int(self.diffusers_in_l[mode_idx][self.cursors_in_l[mode_idx]])]) > limit:
                    self.data[int(self.diffusers_in_l[mode_idx][self.cursors_in_l[mode_idx]])].pop(0)
        else:
            tmp: any = self.BBuffer.pop_s(mode_idx)
            if tmp is not None:
                self.cursors_in_s[mode_idx] = (self.cursors_in_s[mode_idx] + 1) % len(self.diffusers_in_s[mode_idx])
                self.data[int(self.diffusers_in_s[mode_idx][self.cursors_in_s[mode_idx]])].append(tmp)
                if len(self.data[int(self.diffusers_in_s[mode_idx][self.cursors_in_s[mode_idx]])]) > limit:
                    self.data[int(self.diffusers_in_s[mode_idx][self.cursors_in_s[mode_idx]])].pop(0)

    def load_to_BBuffer(self, value: float, item: any) -> None:
        self.BBuffer.add_item(value, item)

    def from_BBuffer_to_Bag(self) -> None:
        self.from_BBuffer_to_Bag_mode_x(math.floor(random.random() * self.num_working_modes))

    def pop_item(self) -> any:
        count: int = 1
        self.cursor_out = (self.cursor_out + 1) % len(self.diffuser_out)
        while not self.data[self.diffuser_out[self.cursor_out]]:
            count += 1
            if count > len(self.diffuser_out):
                return None
            self.cursor_out = (self.cursor_out + 1) % len(self.diffuser_out)
        ret = self.data[self.diffuser_out[self.cursor_out]].pop(0)
        return ret


if __name__ == "__main__":

    record = []

    g = ChaoticNumberGenerator(0.05)
    numbers = g.generate_numbers(50000)

    b = Bag(50, 1000, 10)

    while len(numbers) > 10:
        # 10 in 1 out
        for _ in range(10):
            b.load_to_BBuffer(numbers.pop(), -1)
        b.from_BBuffer_to_Bag()

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
