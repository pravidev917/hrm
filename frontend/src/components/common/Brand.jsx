import { useState } from 'react'
import { brand, brandMonogram } from '../../config/brand'

/**
 * Product logo + name.
 *
 * `center` renders the full lockup (used on the sign-in cards, on white);
 * otherwise the emblem sits in a light chip next to the product name, which is
 * what the dark sidebar needs. When the image cannot load, a monogram mark is
 * shown so the layout never breaks.
 */
export default function Brand({ subtitle, center = false }) {
  const [failed, setFailed] = useState(false)

  if (center) {
    return (
      <div className="brand brand-center">
        {failed ? (
          <>
            <span className="brand-chip">{brandMonogram}</span>
            <span>{brand.name}</span>
          </>
        ) : (
          <img
            className="brand-logo-full"
            src={brand.logoPath}
            alt={brand.name}
            onError={() => setFailed(true)}
          />
        )}
      </div>
    )
  }

  return (
    <div className="brand">
      <span className="brand-chip" aria-hidden="true">
        {failed ? (
          brandMonogram
        ) : (
          <img className="brand-mark-img" src={brand.markPath} alt="" onError={() => setFailed(true)} />
        )}
      </span>
      <span>
        {brand.name}
        {subtitle && <span className="brand-sub">{subtitle}</span>}
      </span>
    </div>
  )
}