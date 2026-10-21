import type { MetricPoint } from "../api";

const palette = [
  "var(--accent)",
  "var(--info)",
  "var(--success)",
  "var(--warning)",
  "var(--danger)",
];

function total(data: MetricPoint[]): number {
  return data.reduce((sum, point) => sum + point.value, 0);
}

export function Donut({ data, label = "Total" }: { data: MetricPoint[]; label?: string }) {
  const sum = total(data);
  if (sum === 0) {
    return <div className="empty">No data yet.</div>;
  }
  const radius = 54;
  const circumference = 2 * Math.PI * radius;
  let offset = 0;
  return (
    <div className="row wrap" style={{ gap: 22 }}>
      <svg width={146} height={146} viewBox="0 0 146 146" role="img" aria-label={`${label}: ${sum}`}>
        <circle cx="73" cy="73" r={radius} fill="none" stroke="var(--surface-2)" strokeWidth="17" />
        {data.map((point, index) => {
          const dash = (point.value / sum) * circumference;
          const segment = (
            <circle
              key={point.label}
              cx="73"
              cy="73"
              r={radius}
              fill="none"
              stroke={palette[index % palette.length]}
              strokeWidth="17"
              strokeDasharray={`${dash} ${circumference - dash}`}
              strokeDashoffset={-offset}
              transform="rotate(-90 73 73)"
            />
          );
          offset += dash;
          return segment;
        })}
        <text x="73" y="70" textAnchor="middle" fontSize="21" fontWeight="700" fill="var(--text)">
          {sum}
        </text>
        <text x="73" y="87" textAnchor="middle" fontSize="10" fill="var(--muted)">
          {label}
        </text>
      </svg>
      <div className="legend" style={{ flexDirection: "column", gap: 6 }}>
        {data.map((point, index) => (
          <span className="legend-item" key={point.label}>
            <span className="swatch" style={{ background: palette[index % palette.length] }} />
            <span>
              {point.label} · {point.value}
            </span>
          </span>
        ))}
      </div>
    </div>
  );
}

export function Bars({ data }: { data: MetricPoint[] }) {
  if (data.length === 0) {
    return <div className="empty">No data yet.</div>;
  }
  const max = Math.max(...data.map((point) => point.value), 1);
  return (
    <div>
      {data.map((point, index) => (
        <div className="bar-row" key={point.label}>
          <span title={point.label} style={{ overflow: "hidden", textOverflow: "ellipsis" }}>
            {point.label}
          </span>
          <div className="bar-track">
            <div
              className="bar-fill"
              style={{
                width: `${Math.max((point.value / max) * 100, 2)}%`,
                background: palette[index % palette.length],
              }}
            />
          </div>
          <span className="bar-value">{point.value}</span>
        </div>
      ))}
    </div>
  );
}

export function LineChart({
  data,
  height = 190,
  unit = "",
}: {
  data: MetricPoint[];
  height?: number;
  unit?: string;
}) {
  if (data.length === 0) {
    return <div className="empty">No data yet.</div>;
  }
  const width = 640;
  const pad = { left: 38, right: 14, top: 14, bottom: 26 };
  const max = Math.max(...data.map((point) => point.value), 1);
  const innerWidth = width - pad.left - pad.right;
  const innerHeight = height - pad.top - pad.bottom;
  const xFor = (index: number) =>
    pad.left + (data.length === 1 ? innerWidth / 2 : (index / (data.length - 1)) * innerWidth);
  const yFor = (value: number) => pad.top + (1 - value / max) * innerHeight;
  const points = data.map((point, index) => ({
    ...point,
    x: xFor(index),
    y: yFor(point.value),
  }));
  const line = points.map((point) => `${point.x},${point.y}`).join(" ");
  const ticks = [0, 0.5, 1];

  return (
    <svg
      className="chart"
      viewBox={`0 0 ${width} ${height}`}
      role="img"
      aria-label={`Line chart with ${data.length} points, maximum ${max}${unit}`}
    >
      {ticks.map((tick) => {
        const value = Math.round(max * tick);
        const y = yFor(value);
        return (
          <g key={tick}>
            <line
              x1={pad.left}
              x2={width - pad.right}
              y1={y}
              y2={y}
              stroke="var(--line)"
              strokeDasharray="3 3"
            />
            <text x={pad.left - 7} y={y + 3} textAnchor="end" fontSize="9" fill="var(--muted)">
              {value}
            </text>
          </g>
        );
      })}
      <polyline points={line} fill="none" stroke="var(--accent)" strokeWidth="2" />
      {points.map((point) => (
        <circle key={`${point.label}-dot`} cx={point.x} cy={point.y} r="3" fill="var(--accent)" />
      ))}
      {[0, Math.floor((data.length - 1) / 2), data.length - 1]
        .filter((value, index, all) => all.indexOf(value) === index)
        .map((index) => (
          <text
            key={`${data[index].label}-label`}
            x={xFor(index)}
            y={height - 7}
            textAnchor="middle"
            fontSize="9"
            fill="var(--muted)"
          >
            {data[index].label}
          </text>
        ))}
    </svg>
  );
}

export function DataTableFallback({ data }: { data: MetricPoint[] }) {
  if (data.length === 0) {
    return null;
  }
  return (
    <details className="small muted" style={{ marginTop: 10 }}>
      <summary>Data table</summary>
      <table className="table" style={{ marginTop: 6 }}>
        <thead>
          <tr>
            <th>Label</th>
            <th className="num">Value</th>
          </tr>
        </thead>
        <tbody>
          {data.map((point) => (
            <tr key={point.label}>
              <td>{point.label}</td>
              <td className="num">{point.value}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </details>
  );
}
