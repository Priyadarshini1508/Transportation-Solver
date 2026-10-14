package com.project.transportation_model.service;

import com.project.transportation_model.model.ModiRequest;
import com.project.transportation_model.model.ModiResponse;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ModiService {

    private static final double EPSILON = 1e-9;

    public ModiResponse processModi(ModiRequest request) {
        ModiResponse response = new ModiResponse();
        double[][] costs = request.getCosts();
        double[][] alloc = request.getAllocation();

        if (costs == null || alloc == null || costs.length == 0 || alloc.length == 0) {
            response.setEligible(false);
            response.setErrorMessage("Invalid matrix dimensions");
            return response;
        }

        int m = alloc.length;
        int n = alloc[0].length;
        response.setM(m);
        response.setN(n);

        int required = m + n - 1;
        response.setRequiredAllocations(required);

        // Count basic cells
        int current = 0;
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                if (alloc[i][j] > 0)
                    current++;
            }
        }
        response.setCurrentAllocations(current);

        // Degeneracy verification for initial solution
        if (current != required) {
            response.setEligible(false);
            response.setErrorMessage("Current solution is degenerate: " + current + " allocations present, but "
                    + required + " required.");
            return response;
        }

        response.setEligible(true);
        List<String> logs = new ArrayList<>();
        logs.add("Initial basic feasible solution satisfies m + n - 1 = " + required + " allocations.");

        // Calculate initial IBFS cost
        double initialCost = computeCost(alloc, costs);
        response.setInitialCost(initialCost);

        // Clone allocation matrix for iterative optimization
        double[][] currentAlloc = new double[m][n];
        for (int i = 0; i < m; i++) {
            System.arraycopy(alloc[i], 0, currentAlloc[i], 0, n);
        }

        int maxIterations = 20;
        int iteration = 0;

        while (iteration < maxIterations) {
            iteration++;
            logs.add("--- Iteration " + iteration + " ---");

            // 1. Calculate u_i and v_j potentials
            Double[] u = new Double[m];
            Double[] v = new Double[n];
            u[0] = 0.0; // Pivot potential

            boolean updated = true;
            while (updated) {
                updated = false;
                for (int i = 0; i < m; i++) {
                    for (int j = 0; j < n; j++) {
                        // Any cell > 0 (including EPSILON) is treated as basic
                        if (currentAlloc[i][j] > 0) {
                            if (u[i] != null && v[j] == null) {
                                v[j] = costs[i][j] - u[i];
                                updated = true;
                            } else if (u[i] == null && v[j] != null) {
                                u[i] = costs[i][j] - v[j];
                                updated = true;
                            }
                        }
                    }
                }
            }

            // Fill unassigned potentials if any disconnected subgraph remains
            for (int i = 0; i < m; i++)
                if (u[i] == null)
                    u[i] = 0.0;
            for (int j = 0; j < n; j++)
                if (v[j] == null)
                    v[j] = 0.0;

            logs.add("Potentials computed: u = " + Arrays.toString(u) + ", v = " + Arrays.toString(v));

            // 2. Compute opportunity costs delta_ij = C_ij - (u_i + v_j) for non-basic cells
            double minDelta = 0;
            int enteringRow = -1;
            int enteringCol = -1;

            for (int i = 0; i < m; i++) {
                for (int j = 0; j < n; j++) {
                    if (currentAlloc[i][j] == 0 && costs[i][j] != -1 && costs[i][j] < 1e9) {
                        double delta = costs[i][j] - (u[i] + v[j]);
                        if (delta < minDelta) {
                            minDelta = delta;
                            enteringRow = i;
                            enteringCol = j;
                        }
                    }
                }
            }

            // 3. Optimality check
            if (minDelta >= 0 || enteringRow == -1) {
                logs.add("All opportunity costs (delta_ij) >= 0. Optimal solution reached.");
                break;
            }

            logs.add("Entering cell identified: Cell (" + (enteringRow + 1) + ", " + (enteringCol + 1)
                    + ") with most negative delta = " + minDelta);

            // 4. Find stepping-stone closed loop starting from (enteringRow, enteringCol)
            List<int[]> loop = findLoop(currentAlloc, enteringRow, enteringCol, m, n);
            if (loop == null || loop.isEmpty()) {
                logs.add("Could not form closed loop. Terminating optimization.");
                break;
            }

            // 5. Determine maximum shift quantity (theta) from donor cells (odd indices)
            double theta = Double.MAX_VALUE;
            for (int k = 1; k < loop.size(); k += 2) {
                int[] cell = loop.get(k);
                theta = Math.min(theta, currentAlloc[cell[0]][cell[1]]);
            }

            logs.add("Loop path traced. Minimum allocation shift (theta) = " + theta);

            // 6. Adjust allocations along alternating loop path
            // Handle intermediate degeneracy: If multiple donor cells hit zero, leave only ONE as 0;
            // keep the others as EPSILON so allocations remain exactly m + n - 1.
            boolean zeroAssigned = false;
            for (int k = 0; k < loop.size(); k++) {
                int[] cell = loop.get(k);
                if (k % 2 == 0) {
                    currentAlloc[cell[0]][cell[1]] += theta;
                } else {
                    double newVal = currentAlloc[cell[0]][cell[1]] - theta;
                    if (Math.abs(newVal) < 1e-7) {
                        if (!zeroAssigned) {
                            currentAlloc[cell[0]][cell[1]] = 0.0;
                            zeroAssigned = true;
                        } else {
                            currentAlloc[cell[0]][cell[1]] = EPSILON; // Retain as epsilon
                        }
                    } else {
                        currentAlloc[cell[0]][cell[1]] = newVal;
                    }
                }
            }
        }

        // Clean up EPSILON values before computing total cost and returning allocations
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                if (currentAlloc[i][j] <= EPSILON) {
                    currentAlloc[i][j] = 0.0;
                }
            }
        }

        double finalCost = Math.round(computeCost(currentAlloc, costs));
        response.setSteps(logs);
        response.setFinalAllocation(currentAlloc);
        response.setFinalCost(finalCost);

        return response;
    }

    private double computeCost(double[][] alloc, double[][] costs) {
        double cost = 0;

        for (int i = 0; i < alloc.length; i++) {
            for (int j = 0; j < alloc[i].length; j++) {

                if (i >= costs.length || j >= costs[i].length) {
                    continue;
                }

                if (alloc[i][j] > EPSILON && costs[i][j] != -1 && costs[i][j] < 1e9) {
                    cost += alloc[i][j] * costs[i][j];
                }
            }
        }

        return cost;
    }

    private List<int[]> findLoop(double[][] alloc, int startR, int startC, int m, int n) {
        List<int[]> path = new ArrayList<>();
        path.add(new int[] { startR, startC });
        if (findPath(alloc, path, true, startR, startC, m, n)) {
            return path;
        }
        return null;
    }

    private boolean findPath(double[][] alloc, List<int[]> path, boolean moveHorizontal, int startR, int startC, int m,
                             int n) {
        int[] last = path.get(path.size() - 1);
        int r = last[0];
        int c = last[1];

        if (path.size() >= 4) {
            if (moveHorizontal && r == startR && c != startC) {
                return true;
            }
            if (!moveHorizontal && c == startC && r != startR) {
                return true;
            }
        }

        if (moveHorizontal) {
            for (int nextC = 0; nextC < n; nextC++) {
                if (nextC != c && (alloc[r][nextC] > 0 || (r == startR && nextC == startC))) {
                    if (r == startR && nextC == startC && path.size() >= 4)
                        return true;
                    if (alloc[r][nextC] > 0 && !containsCell(path, r, nextC)) {
                        path.add(new int[] { r, nextC });
                        if (findPath(alloc, path, false, startR, startC, m, n))
                            return true;
                        path.remove(path.size() - 1);
                    }
                }
            }
        } else {
            for (int nextR = 0; nextR < m; nextR++) {
                if (nextR != r && (alloc[nextR][c] > 0 || (nextR == startR && c == startC))) {
                    if (nextR == startR && c == startC && path.size() >= 4)
                        return true;
                    if (alloc[nextR][c] > 0 && !containsCell(path, nextR, c)) {
                        path.add(new int[] { nextR, c });
                        if (findPath(alloc, path, true, startR, startC, m, n))
                            return true;
                        path.remove(path.size() - 1);
                    }
                }
            }
        }
        return false;
    }

    private boolean containsCell(List<int[]> path, int r, int c) {
        for (int[] p : path) {
            if (p[0] == r && p[1] == c)
                return true;
        }
        return false;
    }
}