/**
 * Product branding in one place.
 *
 * Colours live in `src/styles/tokens.css` (the company purple palette); this
 * file holds the name and the logo paths so a logo swap never touches components.
 *
 * Assets are derived from the delivered logo by `scripts/derive_logo_assets.py`:
 *   public/company-logo.png       original file as delivered (untouched)
 *   public/company-logo-trim.png  trimmed lockup, used on the sign-in card
 *   public/company-mark.png       emblem only with a transparent background,
 *                                 used in the sidebar, small marks and the favicon
 *
 * If an image is missing the UI falls back to a letter monogram, so the build
 * never depends on the asset being present.
 */
export const brand = {
  name: 'PVP Staffing',
  /** Emblem only, transparent background. */
  markPath: '/company-mark.png',
  /** Full lockup (emblem + wordmark + tagline). */
  logoPath: '/company-logo-trim.png',
  subtitleAdmin: 'People operations',
  subtitleEmployee: 'Employee portal',
  signInHeadline: 'Staffing operations in one calm place.',
  signInBlurb: 'Attendance, leave, reports and performance for your whole team — without spreadsheets.',
}

/** First letter used when no logo file is available. */
export const brandMonogram = brand.name.charAt(0).toUpperCase()
