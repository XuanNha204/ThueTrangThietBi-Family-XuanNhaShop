import { useId } from 'react';
export function DeviceArt({
  kind = 'cleaner',
  className = '',
}: {
  kind?: string;
  className?: string;
}) {
  const id = useId().replaceAll(':', '');
  return (
    <svg
      className={`device-art ${className}`}
      viewBox="0 0 280 230"
      role="img"
      aria-label={
        {
          cleaner: 'Máy giặt thảm',
          projector: 'Máy chiếu',
          vacuum: 'Máy hút bụi',
          purifier: 'Máy lọc không khí',
          grill: 'Bếp nướng',
          speaker: 'Loa di động',
          drill: 'Máy khoan',
          fan: 'Quạt làm mát',
        }[kind] ?? 'Thiết bị gia đình'
      }
    >
      <defs>
        <linearGradient id={id} x1="0" y1="0" x2="1" y2="1">
          <stop stopColor="#faf8f2" />
          <stop offset="1" stopColor="#d8d7cf" />
        </linearGradient>
        <linearGradient id={`${id}d`} x1="0" x2="1">
          <stop stopColor="#384943" />
          <stop offset="1" stopColor="#172c26" />
        </linearGradient>
      </defs>
      <ellipse cx="140" cy="207" rx="87" ry="11" fill="#26372f" opacity=".1" />
      {kind === 'projector' ? (
        <>
          <path d="m57 98 147-20 32 26-150 25Z" fill="#fffdf9" />
          <path d="m57 98 29 31v54l-29-24Z" fill="#d7d9d2" />
          <path d="m86 129 150-25v58l-150 27Z" fill={`url(#${id})`} />
          <path
            d="m99 145 47-8m-47 17 47-8m-47 17 47-8m-47 17 47-8"
            stroke="#a4aaa2"
            strokeWidth="3"
          />
          <ellipse cx="191" cy="142" rx="27" ry="25" fill="#35443e" />
          <ellipse cx="193" cy="140" rx="19" ry="18" fill="#6e938f" />
          <ellipse cx="198" cy="134" rx="7" ry="7" fill="#bcd5cf" />
          <path d="m183 92 17-2" stroke="#989f93" strokeWidth="5" strokeLinecap="round" />
        </>
      ) : kind === 'grill' ? (
        <>
          <path d="m43 141 140-47 60 43-140 49Z" fill="#253b33" />
          <path d="m43 141 60 45v14l-60-42Z" fill="#bd6849" />
          <path d="m103 186 140-49v15l-140 48Z" fill="#a04c32" />
          {Array.from({ length: 9 }, (_, i) => (
            <path
              key={i}
              d={`m${62 + i * 13} ${139 - i * 4.3} 47 34`}
              stroke="#91a092"
              strokeWidth="3"
            />
          ))}
          <path
            d="m57 161-9 39m178-44 10 33"
            stroke="#263c33"
            strokeWidth="9"
            strokeLinecap="round"
          />
          <circle cx="124" cy="189" r="7" fill="#eacbb3" />
        </>
      ) : kind === 'drill' ? (
        <>
          <path d="M87 68h93l14 54-56 3-10 67H90l12-74H78Z" fill="#b85c3c" />
          <path d="M78 69H49v48h34" fill={`url(#${id}d)`} />
          <path d="M49 80H25v24h24" fill="#737f76" />
          <path d="M25 89H4v7h21" fill="#a3aaa1" />
          <path d="m102 133 29-3-7 48h-29Z" fill="#233d32" />
          <path d="M82 182h56v20H76Z" fill="#263d34" />
          <path d="m145 86 27-1m-24 11h26m-22 10h25" stroke="#e6b9a3" strokeWidth="3" />
          <rect x="132" y="122" width="11" height="17" rx="3" fill="#253e33" />
        </>
      ) : kind === 'speaker' ? (
        <>
          <path d="M94 46q47-24 89 0v13q-45-19-89 0Z" fill="#253e33" />
          <rect x="77" y="55" width="122" height="147" rx="20" fill={`url(#${id}d)`} />
          <rect x="86" y="65" width="104" height="126" rx="15" fill="#465c50" />
          <circle cx="138" cy="151" r="33" fill="#24372f" />
          <circle cx="138" cy="151" r="24" fill="#516b5c" />
          <circle cx="138" cy="151" r="11" fill="#253d31" />
          <circle cx="138" cy="96" r="20" fill="#283c30" />
          <circle cx="138" cy="96" r="11" fill="#6e8974" />
          <rect
            x="213"
            y="97"
            width="15"
            height="95"
            rx="7"
            transform="rotate(12 220 145)"
            fill="#374c41"
          />
          <rect
            x="209"
            y="79"
            width="23"
            height="37"
            rx="11"
            transform="rotate(12 220 100)"
            fill="#9da79e"
          />
        </>
      ) : kind === 'fan' ? (
        <>
          <rect x="95" y="49" width="99" height="151" rx="14" fill={`url(#${id})`} />
          <path d="M102 61h84v86h-84Z" fill="#c5cec4" />
          {Array.from({ length: 9 }, (_, i) => (
            <path key={i} d={`M108 ${68 + i * 9}h72`} stroke="#7e9283" strokeWidth="3" />
          ))}
          <rect x="105" y="158" width="78" height="27" rx="6" fill="#dde1d6" />
          <path d="M114 48v-8h61v8" fill="none" stroke="#7c9281" strokeWidth="6" />
          <circle cx="115" cy="56" r="3" fill="#b66448" />
          <path d="m108 200-2 8m73-8 2 8" stroke="#425e4b" strokeWidth="8" strokeLinecap="round" />
        </>
      ) : kind === 'purifier' ? (
        <>
          <path d="M92 60q43-20 90 0l12 126q-52 33-114 0Z" fill={`url(#${id})`} />
          <ellipse cx="137" cy="60" rx="45" ry="14" fill="#fefdf7" />
          <ellipse cx="137" cy="60" rx="33" ry="8" fill="#83978a" />
          <circle cx="137" cy="95" r="10" fill="#294737" />
          <circle cx="137" cy="95" r="7" fill="#82b6a6" />
          {Array.from({ length: 11 }, (_, i) => (
            <path key={i} d={`m${94 + i * 8} 125 2 57`} stroke="#b1baad" strokeWidth="2" />
          ))}
          <path d="m105 191 3 12m60-12-3 12" stroke="#617561" strokeWidth="6" />
        </>
      ) : (
        <>
          <path
            d="M109 86V34q0-12 12-12h34q12 0 12 12v52"
            fill="none"
            stroke="#344e3f"
            strokeWidth="9"
          />
          <path d="M109 55h58" stroke="#829680" strokeWidth="6" />
          <rect
            x="89"
            y="83"
            width="103"
            height="109"
            rx="23"
            fill={kind === 'cleaner' ? '#b76848' : `url(#${id})`}
          />
          <path d="M92 102q49-15 97 0v22H92Z" fill={kind === 'cleaner' ? '#dfb098' : '#5b7661'} />
          <path d="M106 134h67v32h-67Z" fill={kind === 'cleaner' ? '#95533a' : '#d0d7cb'} />
          <path
            d="M185 124q48 0 35 48l-9 23"
            fill="none"
            stroke="#354b3d"
            strokeWidth="14"
            strokeLinecap="round"
          />
          <path d="m214 189 16 14h-39Z" fill="#314a3b" />
          <circle cx="106" cy="194" r="12" fill="#2d4536" />
          <circle cx="174" cy="194" r="12" fill="#2d4536" />
          <circle cx="106" cy="194" r="5" fill="#83957e" />
          <circle cx="174" cy="194" r="5" fill="#83957e" />
          <circle cx="141" cy="108" r="4" fill="#f8e9cc" />
        </>
      )}
    </svg>
  );
}
