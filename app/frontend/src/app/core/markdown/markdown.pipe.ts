import { Pipe, PipeTransform } from '@angular/core';
import DOMPurify from 'dompurify';
import { marked } from 'marked';

/** Tags that may reach the DOM (ADR-003 3.3). No `a`: the agent must not link to unknown URLs (3.6). */
const ALLOWED_TAGS = ['p', 'h3', 'h4', 'ul', 'ol', 'li', 'strong', 'em', 'br', 'code'];

/**
 * Markdown to sanitized HTML for the assistant body. Pure and cheap enough to re-run on the full
 * accumulated text; the caller throttles it to one parse per animation frame while streaming.
 * Returns a plain string so Angular's own `[innerHTML]` sanitizer stays as a second layer.
 */
@Pipe({ name: 'markdown' })
export class MarkdownPipe implements PipeTransform {
  transform(markdown: string | null | undefined): string {
    if (!markdown) return '';
    const html = marked.parse(markdown, { async: false, gfm: true });
    return DOMPurify.sanitize(html, { ALLOWED_TAGS, ALLOWED_ATTR: [] });
  }
}
