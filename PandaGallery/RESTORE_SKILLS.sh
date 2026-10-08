#!/bin/bash

# Quick restore from backup
echo "Restoring skills from backup..."

backup_file=$(ls -t ~/.claude/skills-backup-*.tar.gz 2>/dev/null | head -1)

if [ -z "$backup_file" ]; then
  echo "❌ No backup found!"
  echo "Run INSTALL_SKILLS.sh instead"
  exit 1
fi

echo "Found backup: $backup_file"
echo ""
read -p "This will overwrite existing skills. Continue? (y/n) " -n 1 -r
echo

if [[ $REPLY =~ ^[Yy]$ ]]; then
  echo "Restoring..."
  rm -rf ~/.claude/skills
  mkdir -p ~/.claude
  tar -xzf "$backup_file" -C ~/.claude
  echo ""
  echo "✅ Restored!"
  echo "Total skills: $(ls -1 ~/.claude/skills | wc -l)"
else
  echo "Cancelled"
fi
