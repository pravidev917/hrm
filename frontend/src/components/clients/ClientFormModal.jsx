import { clientApi } from '../../api/adminApi'
import FormModal from '../common/FormModal'

/**
 * Client create/edit dialog. The eleven fields are in the order the client form
 * specifies and are sent with their own names: Client ID, Vendor, BDM name,
 * Client name, Position, Contact number, Client HR name, Number of positions,
 * Salary offered, Percentage per candidate, Tax.
 *
 * Legacy counters (profiles submitted, interviews, selections) are not part of
 * this form; the backend keeps whatever is already stored for them.
 */
const FIELDS = [
  { name: 'clientCode', label: 'Client ID', required: true, maxLength: 40, half: true, placeholder: 'CL-1001', hint: 'Unique per organisation' },
  { name: 'vendor', label: 'Vendor', maxLength: 150, half: true },
  { name: 'bdmName', label: 'BDM name', maxLength: 100, half: true },
  { name: 'clientName', label: 'Client name', required: true, maxLength: 150, half: true },
  { name: 'position', label: 'Position', maxLength: 150, half: true, placeholder: 'Java Developer' },
  { name: 'contactNumber', label: 'Contact number', type: 'tel', maxLength: 50, half: true, placeholder: '+91 98765 43210' },
  { name: 'hrName', label: 'Client HR name', maxLength: 100, half: true },
  { name: 'numberOfPositions', label: 'Number of positions', type: 'number', min: 0, step: 1, half: true },
  { name: 'salaryOffered', label: 'Salary offered', type: 'number', min: 0, step: '0.01', half: true, placeholder: '650000' },
  { name: 'budgetPerCandidate', label: 'Budget per candidate', type: 'number', min: 0, step: '0.01', half: true, hint: 'Capacity per opening, not revenue' },
  { name: 'percentagePerCandidate', label: 'Percentage per candidate', type: 'number', min: 0, max: 100, step: '0.01', half: true, hint: '0–100' },
  { name: 'tax', label: 'Tax', type: 'number', min: 0, max: 100, step: '0.01', half: true, hint: 'Percentage, 0–100' },
]

const TWO_DECIMALS = /^\d+(\.\d{1,2})?$/

function validate(v) {
  const errors = {}

  const contact = String(v.contactNumber ?? '').trim()
  if (contact) {
    const digits = contact.replace(/\D/g, '')
    if (!/^[0-9+()\-\s]+$/.test(contact) || digits.length < 6) {
      errors.contactNumber = 'Use at least 6 digits; + ( ) - and spaces are allowed'
    }
  }

  if (String(v.numberOfPositions ?? '').trim() && !/^\d+$/.test(String(v.numberOfPositions).trim())) {
    errors.numberOfPositions = 'Number of positions must be a whole number'
  }

  if (String(v.salaryOffered ?? '').trim() && !TWO_DECIMALS.test(String(v.salaryOffered).trim())) {
    errors.salaryOffered = 'Use a number with at most 2 decimals'
  }
  if (String(v.percentagePerCandidate ?? '').trim() && !TWO_DECIMALS.test(String(v.percentagePerCandidate).trim())) {
    errors.percentagePerCandidate = 'Use a number with at most 2 decimals'
  }
  if (String(v.budgetPerCandidate ?? '').trim() && !TWO_DECIMALS.test(String(v.budgetPerCandidate).trim())) {
    errors.budgetPerCandidate = 'Use a number with at most 2 decimals'
  }
  if (String(v.tax ?? '').trim() && !TWO_DECIMALS.test(String(v.tax).trim())) {
    errors.tax = 'Use a number with at most 2 decimals'
  }

  if (!String(v.clientCode ?? '').trim()) errors.clientCode = 'Client ID is required'
  else if (!/^[A-Za-z0-9][A-Za-z0-9 _./#-]{1,39}$/.test(String(v.clientCode).trim())) {
    errors.clientCode = 'Letters, digits and - _ . / # only (2–40 characters)'
  }

  return errors
}

const text = (value) => {
  const trimmed = String(value ?? '').trim()
  return trimmed === '' ? null : trimmed
}
const num = (value) => {
  const trimmed = String(value ?? '').trim()
  return trimmed === '' ? null : Number(trimmed)
}

const toPayload = (v) => ({
  clientCode: text(v.clientCode),
  vendor: text(v.vendor),
  bdmName: text(v.bdmName),
  clientName: text(v.clientName),
  position: text(v.position),
  contactNumber: text(v.contactNumber),
  hrName: text(v.hrName),
  numberOfPositions: num(v.numberOfPositions),
  salaryOffered: num(v.salaryOffered),
  budgetPerCandidate: num(v.budgetPerCandidate),
  percentagePerCandidate: num(v.percentagePerCandidate),
  tax: num(v.tax),
})

export default function ClientFormModal({ client, onClose, onSaved }) {
  const isEdit = Boolean(client)

  async function submit(values) {
    const payload = toPayload(values)
    if (isEdit) {
      const updated = await clientApi.update(client.clientId, payload)
      onSaved(updated)
    } else {
      const created = await clientApi.create(payload)
      onSaved(created)
    }
    onClose()
  }

  return (
    <FormModal
      wide
      title={isEdit ? `Edit ${client.clientName}` : 'Add client'}
      fields={FIELDS}
      initialValues={client ?? {}}
      validate={validate}
      submitLabel={isEdit ? 'Save changes' : 'Create client'}
      onSubmit={submit}
      onClose={onClose}
      hint={
        isEdit
          ? null
          : () => 'New clients become available immediately in the employee report client lists.'
      }
    />
  )
}
