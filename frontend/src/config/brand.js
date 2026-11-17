/**
 * Product branding in one place.
 *
 * The theme colours live in `src/styles/tokens.css`; this file only holds the
 * name, the logo and the taglines so the logo can be swapped without touching
 * components.
 *
 * Logo: drop the company logo at `public/company-logo.png` (or .svg, then
 * update `logoPath`). Until that file exists a monogram fallback is shown, so
 * the build never depends on it.
 */
export const brand = {
  name: 'HRM',
  logoPath: '/company-logo.png',
  /** Shown under the product name in the sidebar. */
  subtitleAdmin: 'People operations',
  subtitleEmployee: 'Employee portal',
  signInHeadline: 'People operations in one calm place.',
  signInBlurb: 'Attendance, leave, reports and performance for your whole team — without spreadsheets.',
}

/** First letter used when no logo file is available. */
export const brandMonogram = brand.name.charAt(0).toUpperCase()
