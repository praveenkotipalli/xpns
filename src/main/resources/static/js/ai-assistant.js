document.addEventListener('DOMContentLoaded', function () {
    const openBtn = document.getElementById('askAiButton');
    const panel = document.getElementById('aiPanel');
    const closeBtn = document.getElementById('closeAiPanel');
    const askBtn = document.getElementById('askAiSubmit');
    const input = document.getElementById('askAiInput');
    const output = document.getElementById('askAiOutput');

    if (!openBtn || !panel || !closeBtn || !askBtn || !input || !output) {
        return;
    }

    function openPanel() {
        panel.classList.add('open');
        input.focus();
    }

    function closePanel() {
        panel.classList.remove('open');
    }

    function setLoading(isLoading) {
        askBtn.disabled = isLoading;
        askBtn.textContent = isLoading ? 'Thinking...' : 'Ask';
    }

    function currentMonthEnum() {
        const months = [
            'JANUARY', 'FEBRUARY', 'MARCH', 'APRIL', 'MAY', 'JUNE',
            'JULY', 'AUGUST', 'SEPTEMBER', 'OCTOBER', 'NOVEMBER', 'DECEMBER'
        ];
        return months[new Date().getMonth()];
    }

    async function askAi() {
        const question = input.value.trim();
        if (!question) {
            output.textContent = 'Please type a question first.';
            return;
        }

        setLoading(true);
        output.textContent = 'Analyzing your expense data...';

        const payload = {
            question: question,
            year: new Date().getFullYear(),
            month: currentMonthEnum()
        };

        try {
            const response = await fetch('/ai/query', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });

            const result = await response.json();
            if (!response.ok || !result.success) {
                throw new Error(result.message || 'Could not get AI response');
            }

            output.textContent = result.data.answer || 'No response generated.';
        } catch (error) {
            output.textContent = 'Error: ' + error.message;
        } finally {
            setLoading(false);
        }
    }

    openBtn.addEventListener('click', openPanel);
    closeBtn.addEventListener('click', closePanel);
    askBtn.addEventListener('click', askAi);

    input.addEventListener('keydown', function (event) {
        if (event.key === 'Enter' && !event.shiftKey) {
            event.preventDefault();
            askAi();
        }
    });
});
