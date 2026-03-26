package com.project.transportation_model.service;

import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class TransportationService {

    // Internal helper to balance supply and demand with a dummy row/column
    private Object[] balanceProblem(int rows, int cols, double[][] costs, double[] supply, double[] demand) {
        double totalSupply = Arrays.stream(supply).sum();
        double totalDemand = Arrays.stream(demand).sum();

        int newRows = rows;
        int newCols = cols;
        if (totalSupply > totalDemand) newCols++;
        else if (totalDemand > totalSupply) newRows++;

        double[][] newCosts = new double[newRows][newCols];
        double[] newSupply = new double[newRows];
        double[] newDemand = new double[newCols];

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                newCosts[i][j] = costs[i][j];
            }
        }

        if (totalSupply > totalDemand) {
            for (int i = 0; i < rows; i++) newCosts[i][cols] = 0;
            newDemand[cols] = totalSupply - totalDemand;
        }

        if (totalDemand > totalSupply) {
            for (int j = 0; j < cols; j++) newCosts[rows][j] = 0;
            newSupply[rows] = totalDemand - totalSupply;
        }

        System.arraycopy(supply, 0, newSupply, 0, rows);
        System.arraycopy(demand, 0, newDemand, 0, cols);

        return new Object[]{newRows, newCols, newCosts, newSupply, newDemand};
    }

    // --- NORTH WEST CORNER METHOD (Fixed & Re-added) ---
    public double[][] calculateNorthWest(int rows, int cols, double[] supply, double[] demand) {
        // Balance first to ensure math is correct
        double totalSupply = Arrays.stream(supply).sum();
        double totalDemand = Arrays.stream(demand).sum();

        int bRows = (totalDemand > totalSupply) ? rows + 1 : rows;
        int bCols = (totalSupply > totalDemand) ? cols + 1 : cols;

        double[] s = new double[bRows];
        double[] d = new double[bCols];
        System.arraycopy(supply, 0, s, 0, rows);
        System.arraycopy(demand, 0, d, 0, cols);

        if(bRows > rows) s[rows] = totalDemand - totalSupply;
        if(bCols > cols) d[cols] = totalSupply - totalDemand;

        double[][] bAllocation = new double[bRows][bCols];
        int i = 0, j = 0;
        while (i < bRows && j < bCols) {
            double quantity = Math.min(s[i], d[j]);
            bAllocation[i][j] = quantity;
            s[i] -= quantity;
            d[j] -= quantity;
            if (s[i] == 0) i++; else j++;
        }

        // Return original size for UI
        double[][] finalAlloc = new double[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) finalAlloc[r][c] = bAllocation[r][c];
        }
        return bAllocation;
    }

    // --- LEAST COST METHOD ---
    public double[][] calculateLeastCost(int rows, int cols, double[][] costs, double[] supply, double[] demand) {
        Object[] balanced = balanceProblem(rows, cols, costs, supply, demand);
        int bRows = (int) balanced[0];
        int bCols = (int) balanced[1];
        double[][] bCosts = (double[][]) balanced[2];
        double[] bSupply = (double[]) balanced[3];
        double[] bDemand = (double[]) balanced[4];

        double[][] bAllocation = new double[bRows][bCols];
        boolean[] rowDone = new boolean[bRows];
        boolean[] colDone = new boolean[bCols];

        while (true) {
            double minCost = Double.MAX_VALUE;
            List<int[]> ties = new ArrayList<>();
            for (int i = 0; i < bRows; i++) {
                if (rowDone[i]) continue;
                for (int j = 0; j < bCols; j++) {
                    if (colDone[j]) continue;
                    double costVal = (bCosts[i][j] == -1) ? 999999 : bCosts[i][j];
                    if (costVal < minCost) {
                        minCost = costVal;
                        ties.clear();
                        ties.add(new int[]{i, j});
                    } else if (costVal == minCost) {
                        ties.add(new int[]{i, j});
                    }
                }
            }
            if (ties.isEmpty()) break;

            int[] best = ties.get(0);
            double maxAlloc = -1;
            for(int[] cell : ties) {
                double alloc = Math.min(bSupply[cell[0]], bDemand[cell[1]]);
                if(alloc > maxAlloc) { maxAlloc = alloc; best = cell; }
            }

            int r = best[0], c = best[1];
            double qty = Math.min(bSupply[r], bDemand[c]);
            bAllocation[r][c] = qty;
            bSupply[r] -= qty; bDemand[c] -= qty;
            if (bSupply[r] == 0) rowDone[r] = true;
            else colDone[c] = true;
        }

        double[][] finalAlloc = new double[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) finalAlloc[i][j] = bAllocation[i][j];
        }
        return bAllocation;
    }

    // --- VOGEL'S METHOD ---
    public double[][] calculateVogels(int rows, int cols, double[][] costs, double[] supply, double[] demand, List<String> steps) {
        Object[] balanced = balanceProblem(rows, cols, costs, supply, demand);
        int bRows = (int) balanced[0];
        int bCols = (int) balanced[1];
        double[][] bCosts = (double[][]) balanced[2];
        double[] bSupply = (double[]) balanced[3];
        double[] bDemand = (double[]) balanced[4];

        double[][] bAllocation = new double[bRows][bCols];
        boolean[] rowDone = new boolean[bRows];
        boolean[] colDone = new boolean[bCols];
        int iteration = 1;

        while (true) {
            double maxPenalty = -1;
            int pivotIndex = -1;
            boolean isRow = true;
            StringBuilder stepLog = new StringBuilder("Iteration " + iteration + ": ");

            for (int i = 0; i < bRows; i++) {
                if (rowDone[i]) continue;
                List<Double> active = new ArrayList<>();
                for (int j = 0; j < bCols; j++) if (!colDone[j]) active.add(bCosts[i][j] == -1 ? 999999 : bCosts[i][j]);
                if (active.isEmpty()) continue;
                Collections.sort(active);
                double p = (active.size() > 1) ? active.get(1) - active.get(0) : active.get(0);
                if (p > maxPenalty) { maxPenalty = p; pivotIndex = i; isRow = true; }
            }

            for (int j = 0; j < bCols; j++) {
                if (colDone[j]) continue;
                List<Double> active = new ArrayList<>();
                for (int i = 0; i < bRows; i++) if (!rowDone[i]) active.add(bCosts[i][j] == -1 ? 999999 : bCosts[i][j]);
                if (active.isEmpty()) continue;
                Collections.sort(active);
                double p = (active.size() > 1) ? active.get(1) - active.get(0) : active.get(0);
                if (p > maxPenalty) { maxPenalty = p; pivotIndex = j; isRow = false; }
            }

            if (pivotIndex == -1) break;
            stepLog.append("Penalty " + maxPenalty + " in " + (isRow ? "Row " : "Col ") + (pivotIndex+1));
            steps.add(stepLog.toString());

            int i = -1, j = -1;
            if (isRow) {
                i = pivotIndex; double min = Double.MAX_VALUE;
                for (int k = 0; k < bCols; k++) if (!colDone[k] && bCosts[i][k] < min) { min = bCosts[i][k]; j = k; }
            } else {
                j = pivotIndex; double min = Double.MAX_VALUE;
                for (int k = 0; k < bRows; k++) if (!rowDone[k] && bCosts[k][j] < min) { min = bCosts[k][j]; i = k; }
            }

            double qty = Math.min(bSupply[i], bDemand[j]);
            bAllocation[i][j] = qty;
            bSupply[i] -= qty; bDemand[j] -= qty;
            if (bSupply[i] == 0) rowDone[i] = true; else colDone[j] = true;
            iteration++;
        }

        double[][] finalAlloc = new double[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) finalAlloc[i][j] = bAllocation[i][j];
        }
        return bAllocation;
    }
    public double calculateTotalCost(double[][] allocation, double[][] costs) {
        double total = 0;
        int originalRows = costs.length;
        int originalCols = costs[0].length;

        for (int i = 0; i < allocation.length; i++) {
            for (int j = 0; j < allocation[0].length; j++) {
                // ONLY add cost if we are within the ORIGINAL matrix bounds
                // Dummy rows/columns (where i >= originalRows or j >= originalCols) have 0 cost
                if (i < originalRows && j < originalCols) {
                    if (allocation[i][j] > 0 && costs[i][j] != -1) {
                        total += (allocation[i][j] * costs[i][j]);
                    }
                }
            }
        }
        return total;
    }
}