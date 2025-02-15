SET SCHEMA 'zafira';

INSERT INTO SETTINGS (NAME, VALUE, TOOL) VALUES
    ('GOOGLE_CLIENT_SECRET_ORIGIN', '', 'GOOGLE'),
    ('GOOGLE_ENABLED', false, 'GOOGLE'),
    ('LDAP_DN', '', 'LDAP'),
    ('LDAP_SEARCH_FILTER', '', 'LDAP'),
    ('LDAP_URL', '', 'LDAP'),
    ('LDAP_MANAGER_USER', '', 'LDAP'),
    ('LDAP_MANAGER_PASSWORD', '', 'LDAP'),
    ('LDAP_ENABLED', false, 'LDAP'),
    ('JIRA_CLOSED_STATUS', 'CLOSED', 'JIRA'),
    ('JIRA_URL', '', 'JIRA'),
    ('JIRA_USER', '', 'JIRA'),
    ('JIRA_PASSWORD', '', 'JIRA'),
    ('JIRA_ENABLED', false, 'JIRA'),
    ('JENKINS_URL', '', 'JENKINS'),
    ('JENKINS_USER', '', 'JENKINS'),
    ('JENKINS_API_TOKEN_OR_PASSWORD', '', 'JENKINS'),
    ('JENKINS_FOLDER', '', 'JENKINS'),
    ('JENKINS_ENABLED', false, 'JENKINS'),
    ('SLACK_WEB_HOOK_URL', '', 'SLACK'),
    ('SLACK_ENABLED', false, 'SLACK'),
    ('EMAIL_HOST', '', 'EMAIL'),
    ('EMAIL_PORT', '', 'EMAIL'),
    ('EMAIL_USER', '', 'EMAIL'),
    ('EMAIL_FROM_ADDRESS', '', 'EMAIL'),
    ('EMAIL_PASSWORD', '', 'EMAIL'),
    ('EMAIL_ENABLED', false, 'EMAIL'),
    ('AMAZON_ACCESS_KEY', '', 'AMAZON'),
    ('AMAZON_SECRET_KEY', '', 'AMAZON'),
    ('AMAZON_REGION', '', 'AMAZON'),
    ('AMAZON_BUCKET', '', 'AMAZON'),
    ('AMAZON_ENABLED', false, 'AMAZON'),
    ('KEY', '', 'CRYPTO'),
    ('CRYPTO_KEY_SIZE', '128', 'CRYPTO'),
    ('CRYPTO_KEY_TYPE', 'AES', 'CRYPTO'),
    ('RABBITMQ_HOST', '', 'RABBITMQ'),
    ('RABBITMQ_PORT', '', 'RABBITMQ'),
    ('RABBITMQ_USER', '', 'RABBITMQ'),
    ('RABBITMQ_PASSWORD', '', 'RABBITMQ'),
    ('RABBITMQ_ENABLED', false, 'RABBITMQ'),
    ('SELENIUM_URL', '', 'SELENIUM'),
    ('SELENIUM_USER', '', 'SELENIUM'),
    ('SELENIUM_PASSWORD', '', 'SELENIUM'),
    ('SELENIUM_ENABLED', false, 'SELENIUM'),
    ('COMPANY_LOGO_URL', null, null),
    ('LAST_ALTER_VERSION', '122', null);

INSERT INTO PROJECTS (NAME, DESCRIPTION) VALUES ('UNKNOWN', '');


DO $$

  DECLARE SUPER_ADMINS_GROUP_ID GROUPS.id%TYPE;
  DECLARE ADMINS_GROUP_ID GROUPS.id%TYPE;
  DECLARE USERS_GROUP_ID GROUPS.id%TYPE;

  DECLARE USER_ID USER_PREFERENCES.id%TYPE;
  DECLARE PERMISSION_ID PERMISSIONS.id%TYPE;

  BEGIN

    INSERT INTO GROUPS (NAME, ROLE, INVITABLE) VALUES ('Super admins', 'ROLE_ADMIN', FALSE) RETURNING id INTO SUPER_ADMINS_GROUP_ID;
    INSERT INTO GROUPS (NAME, ROLE) VALUES ('Admins', 'ROLE_ADMIN') RETURNING id INTO ADMINS_GROUP_ID;
    INSERT INTO GROUPS (NAME, ROLE) VALUES ('Users', 'ROLE_USER') RETURNING id INTO USERS_GROUP_ID;

    INSERT INTO PERMISSIONS (NAME, BLOCK) VALUES ('VIEW_HIDDEN_DASHBOARDS', 'DASHBOARDS') RETURNING id INTO PERMISSION_ID;
    INSERT INTO GROUP_PERMISSIONS (GROUP_ID, PERMISSION_ID) VALUES (SUPER_ADMINS_GROUP_ID, PERMISSION_ID);

    INSERT INTO PERMISSIONS (NAME, BLOCK) VALUES ('MODIFY_DASHBOARDS', 'DASHBOARDS') RETURNING id INTO PERMISSION_ID;
    INSERT INTO GROUP_PERMISSIONS (GROUP_ID, PERMISSION_ID) VALUES (SUPER_ADMINS_GROUP_ID, PERMISSION_ID),
                                                                   (ADMINS_GROUP_ID, PERMISSION_ID);

    INSERT INTO PERMISSIONS (NAME, BLOCK) VALUES ('MODIFY_WIDGETS', 'DASHBOARDS') RETURNING id INTO PERMISSION_ID;
    INSERT INTO GROUP_PERMISSIONS (GROUP_ID, PERMISSION_ID) VALUES (SUPER_ADMINS_GROUP_ID, PERMISSION_ID),
                                                                   (ADMINS_GROUP_ID, PERMISSION_ID);

    INSERT INTO PERMISSIONS (NAME, BLOCK) VALUES ('MODIFY_TEST_RUN_VIEWS', 'TEST_RUN_VIEWS') RETURNING id INTO PERMISSION_ID;
    INSERT INTO GROUP_PERMISSIONS (GROUP_ID, PERMISSION_ID) VALUES (SUPER_ADMINS_GROUP_ID, PERMISSION_ID);

    INSERT INTO PERMISSIONS (NAME, BLOCK) VALUES ('VIEW_TEST_RUN_VIEWS', 'TEST_RUN_VIEWS') RETURNING id INTO PERMISSION_ID;
    INSERT INTO GROUP_PERMISSIONS (GROUP_ID, PERMISSION_ID) VALUES (SUPER_ADMINS_GROUP_ID, PERMISSION_ID);

    INSERT INTO PERMISSIONS (NAME, BLOCK) VALUES ('MODIFY_TEST_RUNS', 'TEST_RUNS') RETURNING id INTO PERMISSION_ID;
    INSERT INTO GROUP_PERMISSIONS (GROUP_ID, PERMISSION_ID) VALUES (SUPER_ADMINS_GROUP_ID, PERMISSION_ID),
                                                                   (ADMINS_GROUP_ID, PERMISSION_ID),
                                                                   (USERS_GROUP_ID, PERMISSION_ID);

    INSERT INTO PERMISSIONS (NAME, BLOCK) VALUES ('TEST_RUNS_CI', 'TEST_RUNS') RETURNING id INTO PERMISSION_ID;
    INSERT INTO GROUP_PERMISSIONS (GROUP_ID, PERMISSION_ID) VALUES (SUPER_ADMINS_GROUP_ID, PERMISSION_ID),
                                                                   (ADMINS_GROUP_ID, PERMISSION_ID),
                                                                   (USERS_GROUP_ID, PERMISSION_ID);

    INSERT INTO PERMISSIONS (NAME, BLOCK) VALUES ('MODIFY_TESTS', 'TEST_RUNS') RETURNING id INTO PERMISSION_ID;
    INSERT INTO GROUP_PERMISSIONS (GROUP_ID, PERMISSION_ID) VALUES (SUPER_ADMINS_GROUP_ID, PERMISSION_ID),
                                                                   (ADMINS_GROUP_ID, PERMISSION_ID),
                                                                   (USERS_GROUP_ID, PERMISSION_ID);

    INSERT INTO PERMISSIONS (NAME, BLOCK) VALUES ('MODIFY_USERS', 'USERS') RETURNING id INTO PERMISSION_ID;
    INSERT INTO GROUP_PERMISSIONS (GROUP_ID, PERMISSION_ID) VALUES (SUPER_ADMINS_GROUP_ID, PERMISSION_ID);

    INSERT INTO PERMISSIONS (NAME, BLOCK) VALUES ('MODIFY_USER_GROUPS', 'USERS') RETURNING id INTO PERMISSION_ID;
    INSERT INTO GROUP_PERMISSIONS (GROUP_ID, PERMISSION_ID) VALUES (SUPER_ADMINS_GROUP_ID, PERMISSION_ID);

    INSERT INTO PERMISSIONS (NAME, BLOCK) VALUES ('VIEW_USERS', 'USERS') RETURNING id INTO PERMISSION_ID;
    INSERT INTO GROUP_PERMISSIONS (GROUP_ID, PERMISSION_ID) VALUES (SUPER_ADMINS_GROUP_ID, PERMISSION_ID),
                                                                   (ADMINS_GROUP_ID, PERMISSION_ID);

    INSERT INTO PERMISSIONS (NAME, BLOCK) VALUES ('MODIFY_PROJECTS', 'PROJECTS') RETURNING id INTO PERMISSION_ID;
    INSERT INTO GROUP_PERMISSIONS (GROUP_ID, PERMISSION_ID) VALUES (SUPER_ADMINS_GROUP_ID, PERMISSION_ID),
                                                                   (ADMINS_GROUP_ID, PERMISSION_ID),
                                                                   (USERS_GROUP_ID, PERMISSION_ID);

    INSERT INTO PERMISSIONS (NAME, BLOCK) VALUES ('MODIFY_INTEGRATIONS', 'INTEGRATIONS') RETURNING id INTO PERMISSION_ID;
    INSERT INTO GROUP_PERMISSIONS (GROUP_ID, PERMISSION_ID) VALUES (SUPER_ADMINS_GROUP_ID, PERMISSION_ID);

    INSERT INTO PERMISSIONS (NAME, BLOCK) VALUES ('VIEW_INTEGRATIONS', 'INTEGRATIONS') RETURNING id INTO PERMISSION_ID;
    INSERT INTO GROUP_PERMISSIONS (GROUP_ID, PERMISSION_ID) VALUES (SUPER_ADMINS_GROUP_ID, PERMISSION_ID);

    INSERT INTO PERMISSIONS (NAME, BLOCK) VALUES ('INVITE_USERS', 'INVITATIONS') RETURNING id INTO PERMISSION_ID;
    INSERT INTO GROUP_PERMISSIONS (GROUP_ID, PERMISSION_ID) VALUES (SUPER_ADMINS_GROUP_ID, PERMISSION_ID),
                                                                   (ADMINS_GROUP_ID, PERMISSION_ID);

    INSERT INTO PERMISSIONS (NAME, BLOCK) VALUES ('MODIFY_INVITATIONS', 'INVITATIONS') RETURNING id INTO PERMISSION_ID;
    INSERT INTO GROUP_PERMISSIONS (GROUP_ID, PERMISSION_ID) VALUES (SUPER_ADMINS_GROUP_ID, PERMISSION_ID),
                                                                   (ADMINS_GROUP_ID, PERMISSION_ID);

    INSERT INTO PERMISSIONS (NAME, BLOCK) VALUES ('MODIFY_LAUNCHERS', 'LAUNCHERS') RETURNING id INTO PERMISSION_ID;
    INSERT INTO GROUP_PERMISSIONS (GROUP_ID, PERMISSION_ID) VALUES (SUPER_ADMINS_GROUP_ID, PERMISSION_ID),
                                                                   (ADMINS_GROUP_ID, PERMISSION_ID),
                                                                   (USERS_GROUP_ID, PERMISSION_ID);

    INSERT INTO PERMISSIONS (NAME, BLOCK) VALUES ('VIEW_LAUNCHERS', 'LAUNCHERS') RETURNING id INTO PERMISSION_ID;
    INSERT INTO GROUP_PERMISSIONS (GROUP_ID, PERMISSION_ID) VALUES (SUPER_ADMINS_GROUP_ID, PERMISSION_ID),
                                                                   (ADMINS_GROUP_ID, PERMISSION_ID),
                                                                   (USERS_GROUP_ID, PERMISSION_ID);

    INSERT INTO USERS (USERNAME) VALUES ('anonymous') RETURNING id INTO USER_ID;
    INSERT INTO USER_PREFERENCES (NAME, VALUE, USER_ID) VALUES
        ('REFRESH_INTERVAL', '0', USER_ID),
        ('DEFAULT_DASHBOARD', 'General', USER_ID),
        ('THEME', '32', USER_ID);

END$$;
