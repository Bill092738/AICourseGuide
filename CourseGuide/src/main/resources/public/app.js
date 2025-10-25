document.getElementById('submitBtn').addEventListener('click', async () => {
  const major = document.getElementById('major').value.trim();
  const gpa = parseFloat(document.getElementById('gpa').value.trim() || '0');

  const res = await fetch('/api/recommendations', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ major, gpa })
  });

  const data = await res.json();
  const list = document.getElementById('results');
  list.innerHTML = '';
  (data.recommendations || []).forEach(item => {
    const li = document.createElement('li');
    li.textContent = item;
    list.appendChild(li);
  });
});
