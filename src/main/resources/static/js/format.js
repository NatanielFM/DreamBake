// format.js — Formatos argentinos para TODO el sitio (tienda y paneles), paso 6.8.
//
// Un solo lugar para mostrar precios y fechas igual en todas las pantallas:
//   DreamFormat.money(12345)      → "$ 12.345"      (sin centavos si es un número redondo)
//   DreamFormat.money(12345.5)    → "$ 12.345,50"   (con centavos si los tiene)
//   DreamFormat.number(1500.5)    → "1.500,5"       (cantidades: stock, kilos…)
//   DreamFormat.date('2026-10-12')            → "12/10/2026"
//   DreamFormat.dateTime('2026-10-12T15:30')  → "12/10/2026 15:30" (24 hs)
//   DreamFormat.todayISO()        → "2026-10-12" con la fecha de ACÁ (no la de Greenwich: después de las
//                                   21:00 toISOString() ya daba la fecha de mañana)
// El espacio entre "$" y el número es "duro" ( ): así el signo nunca queda solo en otra línea.

(function () {
  const AR = 'es-AR';

  function money(value) {
    const n = Number(value) || 0;
    const hasCents = Math.round(Math.abs(n) * 100) % 100 !== 0;
    return '$ ' + n.toLocaleString(AR, { minimumFractionDigits: hasCents ? 2 : 0, maximumFractionDigits: 2 });
  }

  function number(value, maxDecimals = 2) {
    const n = Number(value) || 0;
    return n.toLocaleString(AR, { maximumFractionDigits: maxDecimals });
  }

  const two = (n) => String(n).padStart(2, '0');

  // Acepta "2026-10-12", "2026-10-12T15:30:00" o un Date. Lo vacío o inválido devuelve "".
  function toDate(value) {
    if (!value) return null;
    if (value instanceof Date) return isNaN(value) ? null : value;
    const m = String(value).match(/^(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2}))?/);
    if (!m) return null;
    return new Date(+m[1], +m[2] - 1, +m[3], +(m[4] || 0), +(m[5] || 0));   // hora local, sin cambio de zona
  }

  function date(value) {
    const d = toDate(value);
    return d ? `${two(d.getDate())}/${two(d.getMonth() + 1)}/${d.getFullYear()}` : '';
  }

  function time(value) {
    const d = toDate(value);
    return d ? `${two(d.getHours())}:${two(d.getMinutes())}` : '';
  }

  function dateTime(value) {
    const d = toDate(value);
    return d ? `${date(d)} ${time(d)}` : '';
  }

  function todayISO(offsetDays = 0) {
    const d = new Date();
    d.setDate(d.getDate() + offsetDays);
    return `${d.getFullYear()}-${two(d.getMonth() + 1)}-${two(d.getDate())}`;
  }

  window.DreamFormat = { money, number, date, time, dateTime, todayISO };
})();
