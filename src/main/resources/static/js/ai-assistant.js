document.addEventListener('DOMContentLoaded', function () {
    const openBtn = document.getElementById('askAiButton');
    const panel = document.getElementById('aiPanel');
    const closeBtn = document.getElementById('closeAiPanel');
    const askBtn = document.getElementById('askAiSubmit');
    const input = document.getElementById('askAiInput');
    const output = document.getElementById('askAiOutput');
    const suggestions = document.getElementById('askAiSuggestions');

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

    function pushMessage(role, text) {
        const item = document.createElement('div');
        item.className = 'ai-msg ' + role;
        item.textContent = text;
        output.appendChild(item);
        output.scrollTop = output.scrollHeight;
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
            pushMessage('bot', 'Please type a question first.');
            return;
        }

        setLoading(true);
        pushMessage('user', question);
        pushMessage('bot', 'Analyzing your expense data...');

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

            const lastBot = output.querySelector('.ai-msg.bot:last-child');
            if (lastBot) {
                lastBot.textContent = result.data.answer || 'No response generated.';
            } else {
                pushMessage('bot', result.data.answer || 'No response generated.');
            }
            input.value = '';
        } catch (error) {
            const lastBot = output.querySelector('.ai-msg.bot:last-child');
            if (lastBot) {
                lastBot.textContent = 'Error: ' + error.message;
            } else {
                pushMessage('bot', 'Error: ' + error.message);
            }
        } finally {
            setLoading(false);
        }
    }

    openBtn.addEventListener('click', openPanel);
    closeBtn.addEventListener('click', closePanel);
    askBtn.addEventListener('click', askAi);

    if (suggestions) {
        suggestions.addEventListener('click', function (event) {
            const button = event.target.closest('.ai-suggestion');
            if (!button) {
                return;
            }
            input.value = button.textContent || '';
            askAi();
        });
    }

    input.addEventListener('keydown', function (event) {
        if (event.key === 'Enter' && !event.shiftKey) {
            event.preventDefault();
            askAi();
        }
    });
});
