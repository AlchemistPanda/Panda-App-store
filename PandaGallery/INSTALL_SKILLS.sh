#!/bin/bash

# Claude Skills One-Shot Installation Script
# This script installs all 4,600+ marketplace skills in a single command

set -e

echo "════════════════════════════════════════════════════════"
echo "   Claude Code Skills Installation (4,600+ Skills)"
echo "════════════════════════════════════════════════════════"
echo ""

# Check if skills directory exists
if [ -d ~/.claude/skills ] && [ $(ls -1 ~/.claude/skills 2>/dev/null | wc -l) -gt 0 ]; then
  echo "⚠️  Existing skills directory found with $(ls -1 ~/.claude/skills | wc -l) skills"
  read -p "Do you want to backup existing skills first? (y/n) " -n 1 -r
  echo
  if [[ $REPLY =~ ^[Yy]$ ]]; then
    backup_file=~/.claude/skills-backup-$(date +%Y%m%d-%H%M%S).tar.gz
    echo "Creating backup: $backup_file"
    tar -czf "$backup_file" -C ~/.claude skills/
    echo "✅ Backup created ($(du -h "$backup_file" | cut -f1))"
  fi
fi

# Create skills directory
mkdir -p ~/.claude/skills

# Clone all major skill repositories
echo ""
echo "Cloning major skill repositories..."
echo ""

mkdir -p /tmp/skill-install-repos
cd /tmp/skill-install-repos

# All major repositories with skills
repos=(
  "anthropics/skills"
  "anthropics/knowledge-work-plugins"
  "vercel-labs/skills"
  "vercel-labs/agent-skills"
  "vercel-labs/agent-browser"
  "microsoft/azure-skills"
  "openai/skills"
  "google-labs-code/stitch-skills"
  "figma/mcp-server-guide"
  "nexu-io/open-design"
  "mattpocock/skills"
  "shadcn/ui"
  "supabase/agent-skills"
  "affaan-m/ecc"
  "affaan-m/everything-claude-code"
  "aj-geddes/useful-ai-prompts"
  "akillness/oh-my-skills"
  "alirezarezvani/claude-skills"
  "arvindrk/extract-design-system"
  "borghei/claude-skills"
  "buildgreatproducts/builder-os"
  "casper-studios/casper-marketplace"
  "community-access/accessibility-agents"
  "connorads/dotfiles"
  "cristicretu/family-taste-skill"
  "dauquangthanh/hanoi-rainbow"
  "davila7/claude-code-templates"
  "dylantarre/animation-principles"
  "ecomfe/tempad-dev"
  "giuseppe-trisciuoglio/developer-kit"
  "hoodini/ai-agents-skills"
  "jezweb/claude-skills"
  "kylezantos/design-engineer-auditor-package"
  "kylezantos/design-motion-principles"
  "leonxlnx/taste-skill"
  "lottiefiles/motion-design-skill"
  "manutej/luxor-claude-marketplace"
  "mastepanoski/claude-skills"
  "mindrally/skills"
  "mosif16/codex-skills"
  "nextlevelbuilder/ui-ux-pro-max-skill"
  "owl-listener/designer-skills"
  "patricio0312rev/skills"
  "plugin87/ux-ui-agent-skills"
  "pproenca/dot-skills"
  "saifyxpro/ui-ux-design-pro-skill"
  "samhvw8/dot-claude"
  "secondsky/claude-skills"
  "sickn33/antigravity-awesome-skills"
  "softaworks/agent-toolkit"
  "telagod/code-abyss"
  "vasilyu1983/ai-agents-public"
  "wshobson/agents"
  "supercent-io/skills-template"
)

cloned=0
failed=0

for repo in "${repos[@]}"; do
  repo_name=$(echo "$repo" | cut -d'/' -f2)
  if [ ! -d "$repo_name" ]; then
    echo -n "Cloning $repo... "
    if git clone "https://github.com/$repo.git" "$repo_name" --depth 1 > /dev/null 2>&1; then
      echo "✓"
      cloned=$((cloned + 1))
    else
      echo "✗ (skipped)"
      failed=$((failed + 1))
    fi
  fi
done

echo ""
echo "Cloned: $cloned repos, Failed: $failed repos"
echo ""

# Extract and install all skills
echo "Extracting and installing skills..."
echo ""

# Find all SKILL.md files and install
find . -maxdepth 5 -type f -name "SKILL.md" 2>/dev/null | while read skill_md; do
  skill_dir=$(dirname "$skill_md")
  skill_name=$(basename "$skill_dir")

  if [ ! -d ~/.claude/skills/"$skill_name" ]; then
    cp -R "$skill_dir" ~/.claude/skills/"$skill_name"
  fi
done

# Also find skills in agent-skills directories
find . -maxdepth 5 -type d -path "*/skills/*" ! -path "*/.*" ! -path "*/node_modules/*" ! -path "*/dist/*" 2>/dev/null | while read skill_path; do
  skill_name=$(basename "$skill_path")

  # Skip common non-skill directories
  if [[ ! "$skill_name" =~ ^(node_modules|dist|build|src|test|tests|spec|docs)$ ]]; then
    if [ -f "$skill_path/SKILL.md" ] || [ -f "$skill_path/README.md" ]; then
      if [ ! -d ~/.claude/skills/"$skill_name" ]; then
        cp -R "$skill_path" ~/.claude/skills/"$skill_name" 2>/dev/null
      fi
    fi
  fi
done

echo ""
echo "════════════════════════════════════════════════════════"
echo "✅ Installation Complete!"
echo "════════════════════════════════════════════════════════"
echo ""
total=$(ls -1 ~/.claude/skills 2>/dev/null | wc -l)
echo "Total skills installed: $total"
echo ""
echo "Next steps:"
echo "  1. Restart Claude Code"
echo "  2. Skills will load automatically"
echo ""
echo "Location: ~/.claude/skills/"
echo "════════════════════════════════════════════════════════"
