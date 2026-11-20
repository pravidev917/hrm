import { apiClient, unwrap } from './axiosClient'

/**
 * Personal notes. The owner is the signed-in account on the server side, so
 * these endpoints never carry an employee or admin id.
 */
function makeNotesApi(base) {
  return {
    list: () => unwrap(apiClient.get(base)),
    create: (body) => unwrap(apiClient.post(base, body)),
    update: (id, body) => unwrap(apiClient.put(`${base}/${id}`, body)),
    remove: (id) => unwrap(apiClient.delete(`${base}/${id}`)),
  }
}

export const notesApi = {
  employee: makeNotesApi('/api/employee/notes'),
  admin: makeNotesApi('/api/admin/notes'),
}
