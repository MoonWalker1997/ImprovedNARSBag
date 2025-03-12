import numpy as np


class ChaoticNumberGenerator:
    def __init__(self, mode_change_prob=0.05):
        self.mode_change_prob = mode_change_prob
        self.mode = "random"
        self.cluster_centers = []
        self.cluster_stds = []
        self.steps_in_current_mode = 0

    def generate_numbers(self, n):
        ret = []
        for _ in range(n):
            if np.random.rand() < self.mode_change_prob:
                self._switch_mode()

            if self.mode == "random":
                num = np.random.uniform(0, 1-1e-5)
                ret.append(num)
            elif self.mode == "clustered":
                idx = np.random.choice(len(self.cluster_centers))
                num = np.random.normal(self.cluster_centers[idx], self.cluster_stds[idx])
                num = np.clip(num, 0, 1-1e-5)
                ret.append(num)

            self.steps_in_current_mode += 1

        return ret

    def _switch_mode(self):
        if self.mode == "random":
            self.mode = "clustered"
            num_clusters = np.random.randint(1, 5)
            self.cluster_centers = np.random.uniform(0.2, 0.8, num_clusters)
            self.cluster_stds = np.random.uniform(0.05, 0.2, num_clusters)
        else:
            self.mode = "random"
        self.steps_in_current_mode = 0
