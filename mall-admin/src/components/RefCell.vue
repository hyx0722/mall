<script setup>
defineProps({
  // 实体名字，缺省（null/空串）时退化为只显示 id
  main: { type: [String, Number], default: null },
  // 实体 id
  id: { type: [String, Number], default: null },
})
</script>

<template>
  <span class="ref">
    <span class="ref-main">{{ main != null && main !== '' ? main : id }}</span>
    <span v-if="main != null && main !== '' && id != null && id !== ''" class="ref-id">({{ id }})</span>
  </span>
</template>

<style scoped>
.ref {
  white-space: nowrap;
}
.ref-main {
  font-weight: 500;
}
.ref-id {
  color: #a8abb2;
  font-size: 12px;
  font-weight: 400;
  margin-left: 3px;
}
</style>
