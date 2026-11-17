import { useState } from 'react'
import { brand, brandMonogram } from '../../config/brand'

/**
 * Product logo + name.
 *
 * Renders the company logo when `brand.logoPath` resolves; otherwise (file not
 * added yet, or it fails to load) a monogram mark is shown so the layout never
 * breaks.
 */
export default function Brand({ subtitle, center = false }) {
  const [logoFailed, setLogoFailed] = useState(false)

  return (
    <div className={center ? 'brand brand-center' : 'brand'}>
      <span className="brand-mark" aria-hidden="true">
        {logoFailed ? (
          brandMonogram
        ) : (
          <img
            className="brand-logo"
            src={brand.logoPath}
            alt=""
            onError={() => setLogoFailed(true)}
          />
        )}
      </span>
      <span>
        {brand.name}
        {subtitle && <span className="brand-sub">{subtitle}</span>}
      </span>
    </div>
  )
}