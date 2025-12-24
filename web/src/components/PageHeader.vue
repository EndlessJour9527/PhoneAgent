<template>
  <div class="page-header-unified">
    <div class="header-left-unified">
      <h2 class="page-title-unified">{{ title }}</h2>
      <p v-if="subtitle" class="page-subtitle-unified">{{ subtitle }}</p>
    </div>
    <div class="header-right-unified">
      <slot name="actions"></slot>
    </div>
  </div>
</template>

<script setup>
defineProps({
  title: {
    type: String,
    required: true
  },
  subtitle: {
    type: String,
    default: ''
  }
})
</script>

<style scoped>
.page-header-unified {
  max-width: 1400px;
  margin: 0 auto;
  padding: var(--space-xl) var(--space-lg) var(--space-lg);
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-lg);
}

.header-left-unified {
  flex: 1;
  min-width: 0;
}

.page-title-unified {
  margin: 0;
  font-size: 28px;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.2;
}

.page-subtitle-unified {
  margin: var(--space-xs) 0 0;
  font-size: 16px;
  color: var(--text-secondary);
  line-height: 1.4;
}

.header-right-unified {
  display: flex;
  gap: var(--space-sm);
  align-items: center;
  flex-shrink: 0;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .page-header-unified {
    flex-direction: column;
    align-items: flex-start;
    padding: var(--space-lg) var(--space-md);
  }
  
  .page-title-unified {
    font-size: 24px;
  }
  
  .page-subtitle-unified {
    font-size: 14px;
  }
  
  .header-right-unified {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>