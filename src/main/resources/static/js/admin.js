// API endpoints
const API_URL = '/api/admin';

// *** НОВЫЙ ФУНКЦИЯ: Загрузка ролей ***
async function loadRoles() {
    try {
        const response = await fetch(`${API_URL}/roles`);  // ← /api/admin/roles
        const roles = await response.json();
        populateRolesCheckboxes(roles);  // Динамически заполняем чекбоксы
    } catch (error) {
        console.error('Error loading roles:', error);
    }
}

// *** НОВАЯ ФУНКЦИЯ: Заполнение чекбоксов ролями ***
function populateRolesCheckboxes(roles) {
    const container = document.getElementById('newRolesContainer');
    container.innerHTML = '';  // Очищаем старые чекбоксы

    roles.forEach(role => {
        const div = document.createElement('div');
        div.className = 'form-check';
        div.innerHTML = `
            <input class="form-check-input" type="checkbox" 
                   id="role_${role.id}" name="roleIds" value="${role.id}">
            <label class="form-check-label" for="role_${role.id}">
                ${role.name}
            </label>
        `;
        container.appendChild(div);
    });
}

// Загрузка всех пользователей
async function loadUsers() {
    try {
        const response = await fetch(API_URL);
        const users = await response.json();
        displayUsers(users);
    } catch (error) {
        console.error('Error loading users:', error);
    }
}

// Отображение пользователей в таблице
function displayUsers(users) {
    const tbody = document.querySelector('#usersTable tbody');
    tbody.innerHTML = '';

    users.forEach(user => {
        const row = createUserRow(user);
        tbody.appendChild(row);
    });
}

// Создание строки таблицы
function createUserRow(user) {
    const row = document.createElement('tr');
    const roles = user.roles.map(r => r.name).join(' ');

    row.innerHTML = `
        <td>${user.id}</td>
        <td>${user.name}</td>
        <td>${user.surname}</td>
        <td>${roles}</td>
        <td>
            <button class="btn btn-info" onclick="openEditModal(${user.id})">Edit</button>
        </td>
        <td>
            <button class="btn btn-danger" onclick="openDeleteModal(${user.id})">Delete</button>
        </td>
    `;
    return row;
}

// Открыть модальное окно редактирования
async function openEditModal(userId) {
    try {
        const response = await fetch(`${API_URL}/${userId}`);
        const user = await response.json();

        document.getElementById('editUserId').value = user.id;
        document.getElementById('editName').value = user.name;
        document.getElementById('editSurname').value = user.surname;
        document.getElementById('editUsername').value = user.username;
        document.getElementById('editPassword').value = '';

        // *** НОВОЕ: Загружаем роли для формы редактирования ***
        await loadRolesForEdit(user.roles);

        $('#editModal').modal('show');
    } catch (error) {
        console.error('Error loading user:', error);
    }
}

// *** НОВАЯ ФУНКЦИЯ: Роли для формы редактирования (нужны модалки с чекбоксами!) ***
async function loadRolesForEdit(userRoles) {
    try {
        const response = await fetch(`${API_URL}/roles`);
        const allRoles = await response.json();

        const container = document.getElementById('editRolesContainer');
        container.innerHTML = '';

        allRoles.forEach(role => {
            const div = document.createElement('div');
            div.className = 'form-check';

            const isChecked = userRoles.some(ur => ur.id === role.id);

            div.innerHTML = `
                <input class="form-check-input" type="checkbox"
                       id="edit_role_${role.id}"
                       value="${role.id}"
                       ${isChecked ? 'checked' : ''}>
                <label class="form-check-label" for="edit_role_${role.id}">
                    ${role.name}
                </label>
            `;
            container.appendChild(div);
        });
    } catch (error) {
        console.error('Error loading roles for edit:', error);
    }
}


// Обновить пользователя (РОЛИ НЕ ОТПРАВЛЯЮТСЯ - нужна модалка с чекбоксами!)
async function updateUser(event) {
    event.preventDefault();

    const userId = document.getElementById('editUserId').value;
    const password = document.getElementById('editPassword').value;

    const userData = {
        name: document.getElementById('editName').value,
        surname: document.getElementById('editSurname').value,
        username: document.getElementById('editUsername').value
    };

    if (password) {
        userData.password = password;
    }

    // *** TODO: Добавить роли из чекбоксов редактирования ***
    const checkedRolesEdit = Array.from(document.querySelectorAll('#editRolesContainer input:checked'))
         .map(ch => ({ id: Number(ch.value) }));
    userData.roles = checkedRolesEdit;

    try {
        const response = await fetch(`${API_URL}/${userId}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(userData)
        });

        if (response.ok) {
            $('#editModal').modal('hide');
            await loadUsers();
        }
    } catch (error) {
        console.error('Error updating user:', error);
    }
}

// *** УДАЛЕНО: openDeleteModal и deleteUser остаются без изменений ***

// Создать нового пользователя
async function createUser(event) {
    event.preventDefault();

    // *** ИСПРАВЛЕНО: Теперь динамические чекбоксы работают! ***
    const checkedRoles = Array.from(
        document.querySelectorAll('#newRolesContainer input[type="checkbox"]:checked')
    ).map(ch => ({ id: Number(ch.value) }));  // ← Теперь value из API!

    const userData = {
        name: document.getElementById('newName').value,
        surname: document.getElementById('newSurname').value,
        username: document.getElementById('newUsername').value,
        password: document.getElementById('newPassword').value,
        roles: checkedRoles  // ← Теперь динамические роли!
    };

    try {
        const response = await fetch(API_URL, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(userData)
        });

        if (response.ok) {
            document.getElementById('newUserForm').reset();
            $('#users-tab').tab('show');
            await loadUsers();
        }
    } catch (error) {
        console.error('Error creating user:', error);
    }
}

// Инициализация при загрузке страницы
document.addEventListener('DOMContentLoaded', function() {
    // *** ИСПРАВЛЕНО: Загружаем роли ПЕРВЫМИ! ***
    loadRoles();  // ← НОВОЕ!
    loadUsers();

    // Привязка обработчиков к формам (без изменений)
    const editForm = document.getElementById('editUserForm');
    if (editForm) {
        editForm.addEventListener('submit', updateUser);
    }

    const deleteForm = document.getElementById('deleteUserForm');
    if (deleteForm) {
        deleteForm.addEventListener('submit', deleteUser);
    }

    const newForm = document.getElementById('newUserForm');
    if (newForm) {
        newForm.addEventListener('submit', createUser);
    }
});
