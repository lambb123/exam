import request from '@/utils/request'

export const getDbTables = () => {
  return request({
    url: '/api/schema/tables',
    method: 'get'
  })
}

export const getTableColumns = (tableName: string) => {
  return request({
    url: '/api/schema/columns',
    method: 'get',
    params: { tableName }
  })
}
