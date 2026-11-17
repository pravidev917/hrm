import { adminEmployeeApi } from '../../api/adminApi'
import FormModal from '../common/FormModal'

/**
 * Shared create/edit dialog for employee accounts.
 *
 * Only administrators reach this dialog: the application has no
 * self-registration, so every account starts here.
 *
 * - create: employee ID, email and phone are chosen by the administrator and are
 *   all valid sign-in identifiers, so each must be unique. Password is required.
 * - edit:   password is optional and only replaced when filled in.
 */
export default function EmployeeFormModal({ employee, onClose, onSaved }) {
  const isEdit = Boolean(employee)

  const fields = [
    { name: 'employeeName', label: 'Full name', required: true, maxLength: 100, half: true, autoComplete: 'off' },
    {
      name: 'employeeCode',
      label: 'Employee ID',
      required: true,
      maxLength: 40,
      half: true,
      placeholder: 'EMP-0007',
      hint: 'Can be used to sign in',
    },
    { name: 'email', label: 'Email', type: 'email', required: true, maxLength: 150, half: true, autoComplete: 'off' },
    {
      name: 'contact',
      label: 'Phone',
      required: true,
      maxLength: 50,
      half: true,
      placeholder: '98765 43210',
      hint: 'Can be used to sign in',
    },
    { name: 'department', label: 'Department', required: true, maxLength: 100, half: true, placeholder: 'Recruitment' },
    { name: 'role', label: 'Job designation', required: true, maxLength: 50, half: true, placeholder: 'Recruiter' },
    {
      name: 'salary',
      label: 'Salary',
      type: 'number',
      min: 0,
      step: '0.01',
      half: true,
      placeholder: '45000',
    },
    {
      name: 'password',
      label: isEdit ? 'New password' : 'Password',
      type: 'password',
      required: !isEdit,
      minLength: 8,
      half: true,
      autoComplete: 'new-password',
      hint: isEdit ? 'Leave blank to keep the current password' : 'At least 8 characters',
    },
    {
      name: 'dateOfBirth',
      label: 'Date of birth',
      type: 'date',
      half: true,
      hint: 'Shows as a birthday on the calendar',
    },
    {
      name: 'joiningDate',
      label: 'Joining date',
      type: 'date',
      half: true,
      hint: 'Drives work anniversaries',
    },
  ]

  async function submit(values) {
    const payload = {
      employeeName: values.employeeName.trim(),
      employeeCode: values.employeeCode.trim(),
      email: values.email.trim(),
      contact: values.contact.trim(),
      department: values.department.trim(),
      role: values.role.trim(),
      // Empty salary input submits '' — send null so the column is cleared.
      salary: values.salary === '' || values.salary == null ? null : Number(values.salary),
      // Empty dates submit '' — send null so the field is cleared.
      dateOfBirth: values.dateOfBirth || null,
      joiningDate: values.joiningDate || null,
    }

    if (isEdit) {
      if (values.password) payload.password = values.password
      const updated = await adminEmployeeApi.update(employee.employeeId, payload)
      onSaved(updated, 'updated')
    } else {
      payload.password = values.password
      const created = await adminEmployeeApi.create(payload)
      onSaved(created, 'created')
    }
    onClose()
  }

  return (
    <FormModal
      title={isEdit ? `Edit ${employee.employeeName}` : 'Add employee'}
      wide
      fields={fields}
      initialValues={employee ?? {}}
      submitLabel={isEdit ? 'Save changes' : 'Create employee'}
      onSubmit={submit}
      onClose={onClose}
      hint={
        isEdit
          ? null
          : () => 'The employee can sign in right away with the employee ID, email or phone number you set here.'
      }
    />
  )
}
