INSERT INTO role_permissions (role_id, permission)
  SELECT id, 'GROUP_PERMISSION_CHANGE_PERMISSION_TEMPLATE' FROM role WHERE role_name = 'ROLE_GROUP_ORGANIZER';

