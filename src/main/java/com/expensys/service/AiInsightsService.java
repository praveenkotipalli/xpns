package com.expensys.service;

import com.expensys.entity.ExpenseEntity;
import com.expensys.model.enums.Month;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.*;

@Service
public class AiInsightsService {
    private final ExpenseService expenseService;
    private final OllamaService ollamaService;

    public AiInsightsService(ExpenseService expenseService, OllamaService ollamaService) {
        this.expenseService = expenseService;
        this.ollamaService = ollamaService;
    }

    public String answerQuestion(String question, Integer year, Month month) {
        YearMonth currentPeriod = resolvePeriod(year, month);
        YearMonth previousPeriod = currentPeriod.minusMonths(1);

        Month currentMonth = Month.valueOf(currentPeriod.getMonth().name());
        Month previousMonth = Month.valueOf(previousPeriod.getMonth().name());

        List<ExpenseEntity> currentExpenses =
                expenseService.getExpenseEntitiesByMonthAndYear(currentPeriod.getYear(), currentMonth);
        List<ExpenseEntity> previousExpenses =
                expenseService.getExpenseEntitiesByMonthAndYear(previousPeriod.getYear(), previousMonth);

        if (currentExpenses.isEmpty()) {
            return "I could not find any expenses for " + readableMonth(currentPeriod) + ". Add a few entries first, then I can analyze who spent more and where to reduce costs.";
        }

        double currentTotal = total(currentExpenses);
        double previousTotal = total(previousExpenses);
        Map<String, Double> spentByTotals = totalsBySpentBy(currentExpenses);
        Map<String, Double> categoryTotals = totalsByCategory(currentExpenses);
        String llmContext = buildContext(question, currentPeriod, previousPeriod, currentTotal, previousTotal, spentByTotals, categoryTotals);

        Optional<String> llmAnswer = ollamaService.generate(llmContext);
        if (llmAnswer.isPresent()) {
            return llmAnswer.get();
        }

        String normalizedQuestion = question == null ? "" : question.toLowerCase(Locale.ROOT);
        StringBuilder response = new StringBuilder();

        response.append("Here is your ").append(readableMonth(currentPeriod)).append(" spending insight:\n");
        response.append("- Total spent: ").append(inr(currentTotal)).append(".\n");
        appendMoMChange(response, currentTotal, previousTotal, previousPeriod);

        if (looksLikeWhoSpentMoreQuestion(normalizedQuestion)) {
            appendTopSpender(response, spentByTotals);
        } else {
            appendSpenderSplit(response, spentByTotals);
        }

        appendTopCategories(response, categoryTotals);
        appendSavingsTips(response, categoryTotals, spentByTotals, currentTotal);

        return response.toString().trim();
    }

    private String buildContext(String question,
                                YearMonth currentPeriod,
                                YearMonth previousPeriod,
                                double currentTotal,
                                double previousTotal,
                                Map<String, Double> spentByTotals,
                                Map<String, Double> categoryTotals) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a concise expense analysis assistant.\n")
                .append("Rules:\n")
                .append("- Use only the provided data.\n")
                .append("- If data is missing, say so clearly.\n")
                .append("- Keep answer under 160 words.\n")
                .append("- Include practical saving tips.\n\n")
                .append("User question: ").append(question == null ? "" : question).append("\n\n")
                .append("Data:\n")
                .append("Current month: ").append(readableMonth(currentPeriod)).append("\n")
                .append("Current total: ").append(inr(currentTotal)).append("\n")
                .append("Previous month: ").append(readableMonth(previousPeriod)).append("\n")
                .append("Previous total: ").append(inr(previousTotal)).append("\n")
                .append("Spent by:\n");

        for (Map.Entry<String, Double> entry : spentByTotals.entrySet()) {
            prompt.append("- ").append(entry.getKey()).append(": ").append(inr(entry.getValue())).append("\n");
        }

        prompt.append("Category totals:\n");
        for (Map.Entry<String, Double> entry : categoryTotals.entrySet()) {
            prompt.append("- ").append(humanizeCategory(entry.getKey())).append(": ").append(inr(entry.getValue())).append("\n");
        }
        return prompt.toString();
    }

    private YearMonth resolvePeriod(Integer year, Month month) {
        LocalDate now = LocalDate.now();
        int resolvedYear = year != null ? year : now.getYear();
        Month resolvedMonth = month != null && month != Month.ALL ? month : Month.valueOf(now.getMonth().name());
        return YearMonth.of(resolvedYear, Integer.parseInt(resolvedMonth.getMonthValue()));
    }

    private void appendMoMChange(StringBuilder response, double currentTotal, double previousTotal, YearMonth previousPeriod) {
        if (previousTotal <= 0) {
            response.append("- No previous month data to compare.\n");
            return;
        }
        double diff = currentTotal - previousTotal;
        double pct = (diff / previousTotal) * 100.0;
        if (diff > 0) {
            response.append("- Month-over-month: up by ")
                    .append(inr(diff))
                    .append(" (")
                    .append(String.format(Locale.ROOT, "%.1f", pct))
                    .append("%) vs ")
                    .append(readableMonth(previousPeriod))
                    .append(".\n");
        } else if (diff < 0) {
            response.append("- Month-over-month: down by ")
                    .append(inr(Math.abs(diff)))
                    .append(" (")
                    .append(String.format(Locale.ROOT, "%.1f", Math.abs(pct)))
                    .append("%) vs ")
                    .append(readableMonth(previousPeriod))
                    .append(".\n");
        } else {
            response.append("- Month-over-month: unchanged vs ").append(readableMonth(previousPeriod)).append(".\n");
        }
    }

    private void appendTopSpender(StringBuilder response, Map<String, Double> spentByTotals) {
        Map.Entry<String, Double> leader = spentByTotals.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElse(null);
        if (leader == null) {
            response.append("- I could not identify spender split for this month.\n");
            return;
        }
        response.append("- Who spent more: ").append(leader.getKey())
                .append(" with ").append(inr(leader.getValue())).append(".\n");
        appendSpenderSplit(response, spentByTotals);
    }

    private void appendSpenderSplit(StringBuilder response, Map<String, Double> spentByTotals) {
        if (spentByTotals.isEmpty()) {
            response.append("- Spent-by split is unavailable.\n");
            return;
        }
        response.append("- Spent-by split: ");
        List<Map.Entry<String, Double>> sorted = new ArrayList<>(spentByTotals.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<String, Double> entry = sorted.get(i);
            response.append(entry.getKey()).append(" ").append(inr(entry.getValue()));
            if (i < sorted.size() - 1) {
                response.append(", ");
            }
        }
        response.append(".\n");
    }

    private void appendTopCategories(StringBuilder response, Map<String, Double> categoryTotals) {
        if (categoryTotals.isEmpty()) {
            response.append("- Category data is unavailable.\n");
            return;
        }
        List<Map.Entry<String, Double>> sorted = new ArrayList<>(categoryTotals.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        response.append("- Top categories: ");
        int limit = Math.min(3, sorted.size());
        for (int i = 0; i < limit; i++) {
            Map.Entry<String, Double> entry = sorted.get(i);
            response.append(humanizeCategory(entry.getKey())).append(" ").append(inr(entry.getValue()));
            if (i < limit - 1) {
                response.append(", ");
            }
        }
        response.append(".\n");
    }

    private void appendSavingsTips(StringBuilder response, Map<String, Double> categoryTotals, Map<String, Double> spentByTotals, double currentTotal) {
        response.append("- Suggestions to reduce next month expenses:\n");

        List<Map.Entry<String, Double>> sortedCategories = new ArrayList<>(categoryTotals.entrySet());
        sortedCategories.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        int tips = 0;
        for (Map.Entry<String, Double> entry : sortedCategories) {
            if (tips >= 3) {
                break;
            }
            double value = entry.getValue();
            double share = currentTotal <= 0 ? 0 : (value / currentTotal) * 100.0;
            if (share < 12) {
                continue;
            }
            double possibleSave = value * 0.15;
            response.append("  1) Reduce ")
                    .append(humanizeCategory(entry.getKey()))
                    .append(" by 10-15% (possible save: ")
                    .append(inr(possibleSave))
                    .append(").\n");
            tips++;
        }

        if (tips == 0) {
            response.append("  1) Set a weekly cap for discretionary spending and review spends every Sunday.\n");
            tips++;
        }

        if (spentByTotals.size() > 1) {
            response.append("  2) Set per-person weekly limits so spending is balanced between users.\n");
            tips++;
        }

        if (tips < 3) {
            response.append("  3) Track top 5 expense items and pause non-essential repeats for 2 weeks.\n");
        }
    }

    private boolean looksLikeWhoSpentMoreQuestion(String question) {
        return question.contains("who spent more")
                || question.contains("spent more")
                || question.contains("spender")
                || question.contains("who spent");
    }

    private Map<String, Double> totalsBySpentBy(List<ExpenseEntity> expenses) {
        Map<String, Double> totals = new HashMap<>();
        for (ExpenseEntity expense : expenses) {
            String key = expense.getSpentBy() == null || expense.getSpentBy().isBlank()
                    ? "Unknown"
                    : expense.getSpentBy().trim();
            totals.merge(key, safeAmount(expense.getSpent()), Double::sum);
        }
        return totals;
    }

    private Map<String, Double> totalsByCategory(List<ExpenseEntity> expenses) {
        Map<String, Double> totals = new HashMap<>();
        for (ExpenseEntity expense : expenses) {
            String key = expense.getCategory() == null || expense.getCategory().isBlank()
                    ? "OTHERS"
                    : expense.getCategory().trim();
            totals.merge(key, safeAmount(expense.getSpent()), Double::sum);
        }
        return totals;
    }

    private double total(List<ExpenseEntity> expenses) {
        return expenses.stream().mapToDouble(e -> safeAmount(e.getSpent())).sum();
    }

    private double safeAmount(Double amount) {
        return amount == null ? 0 : amount;
    }

    private String readableMonth(YearMonth yearMonth) {
        return yearMonth.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + yearMonth.getYear();
    }

    private String humanizeCategory(String rawCategory) {
        if (rawCategory == null || rawCategory.isBlank()) {
            return "Others";
        }
        String[] parts = rawCategory.toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            builder.append(Character.toUpperCase(part.charAt(0)))
                    .append(part.substring(1))
                    .append(" ");
        }
        return builder.toString().trim();
    }

    private String inr(double amount) {
        return "₹" + String.format(Locale.ROOT, "%,.2f", amount);
    }
}
