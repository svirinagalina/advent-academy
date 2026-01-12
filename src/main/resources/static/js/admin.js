// API endpoints
const API_URL = '/api/admin';

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

        $('#editModal').modal('show');
    } catch (error) {
        console.error('Error loading user:', error);
    }
}

// Обновить пользователя
async function updateUser(event) {
    event.preventDefault();

    const userId = document.getElementById('editUserId').value;
    const password = document.getElementById('editPassword').value;

    const userData = {
        name: document.getElementById('editName').value,
        surname: document.getElementById('editSurname').value,
        username: document.getElementById('editUsername').value
    };

    // Добавляем пароль только если он указан
    if (password) {
        userData.password = password;
    }

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

// Открыть модальное окно удаления
async function openDeleteModal(userId) {
    try {
        const response = await fetch(`${API_URL}/${userId}`);
        const user = await response.json();

        document.getElementById('deleteUserId').value = user.id;
        document.getElementById('deleteUserInfo').innerHTML = `
            <p><strong>ID:</strong> ${user.id}</p>
            <p><strong>Name:</strong> ${user.name} ${user.surname}</p>
            <p><strong>Username:</strong> ${user.username}</p>
        `;

        $('#deleteModal').modal('show');
    } catch (error) {
        console.error('Error loading user:', error);
    }
}

// Удалить пользователя
async function deleteUser(event) {
    event.preventDefault();

    const userId = document.getElementById('deleteUserId').value;

    try {
        const response = await fetch(`${API_URL}/${userId}`, {
            method: 'DELETE'
        });

        if (response.ok) {
            $('#deleteModal').modal('hide');
            await loadUsers();
        }
    } catch (error) {
        console.error('Error deleting user:', error);
    }
}

// Создать нового пользователя
async function createUser(event) {
    event.preventDefault();

    // Получаем отмеченные чекбоксы ролей
    const checkedRoles = Array.from(
        document.querySelectorAll('#newRolesContainer input[type="checkbox"]:checked')
    ).map(ch => ch.value); // тут id роли

    const userData = {
        name: document.getElementById('newName').value,
        surname: document.getElementById('newSurname').value,
        username: document.getElementById('newUsername').value,
        password: document.getElementById('newPassword').value,
        // передаём роли в теле запроса
        roles: checkedRoles.map(id => ({ id: Number(id) }))
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
    loadUsers();

    // Привязка обработчиков к формам
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
