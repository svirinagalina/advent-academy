package ru.adventacademy.algorithms.linkedlist;

//Даны два отсортированных связных списка list1 и list2.
//Нужно слить их в один отсортированный список, используя узлы из этих двух списков.
//Вернуть голову (начало) объединённого списка.
// list1 = 1 3 5    list2 = 2,4,6     1,2,3,4,5,6

import java.util.LinkedList;

class ListNode {
    int val;
    ListNode next;

    public static class Main {

        public ListNode merge(ListNode list1, ListNode list2) {
            ListNode entryPoint = new ListNode(0);
            ListNode current = entryPoint;

            while (list1 != null && list2 != null) {
                if (list1.val() <= list2.val()) {
                    current.next = list1;
                    list1 = list1.next;
                } else {
                    current.next = list2;
                    list2 = list2.next;
                }
                current = current.next;
            }
            
return entryPoint;
        }
    }
}
