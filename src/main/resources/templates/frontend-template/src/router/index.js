import { createRouter, createWebHistory } from 'vue-router'
import Home from '@/views/Home.vue'
import Project from '@/views/Project.vue'

const routes = [
    {
        path: '/',
        name: 'Home',
        component: Home
    },
    {
        path: '/project',
        name: 'Project',
        component: Project
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

export default router