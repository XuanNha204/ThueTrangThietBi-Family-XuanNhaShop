export type User = {
  id: string;
  username: string;
  displayName: string;
  role: string;
  customerId: string | null;
  enabled: boolean;
};
export type Product = {
  id: string;
  code: string;
  name: string;
  category: string;
  description: string;
  specifications: string;
  icon: string;
  dailyRate: number;
  priceVersion: number;
  deposit: number;
  policyVersion: string;
  available?: number;
  photo?: {
    id: string;
    illustrative: boolean;
    credit: string | null;
    sourceUrl: string | null;
    licenseUrl: string | null;
  };
};
export type Customer = {
  id: string;
  name: string;
  phone: string;
  email: string | null;
  address: string;
};
export type Equipment = {
  id: string;
  code: string;
  type_id: string;
  name: string;
  category: string;
  state: string;
  warehouse: string;
};
export type RentalSummary = {
  id: string;
  code: string;
  customer_name: string;
  status: string;
  starts_at: string;
  ends_at: string;
  expires_at: string;
  rental_total: number;
  deposit_required: number;
  item_count: number;
  created_at: string;
};
export type RentalOrder = {
  id: string;
  code: string;
  customerId: string;
  customerName: string;
  status: string;
  startsAt: string;
  endsAt: string;
  expiresAt: string;
  rentalTotal: number;
  depositRequired: number;
  address: string;
  terms: string;
  agreementVersion: string;
  version: number;
};
export type RentalItem = {
  id: string;
  type_id: string;
  equipment_id: string;
  equipment_code: string;
  type_name: string;
  handed_at: string | null;
  returned_at: string | null;
  return_condition: string | null;
  resolved_at: string | null;
  resolution_kind: string | null;
  equipment_state: string;
  rental_amount: number;
  deposit_amount: number;
};
export type RentalDetail = {
  order: RentalOrder;
  items: RentalItem[];
  balance: { depositHeld: number; rentalPaid: number; chargesDue: number; chargesPaid: number };
};
export type Quote = {
  rentalTotal: number;
  depositRequired: number;
  holdMinutes: number;
  bufferMinutes: number;
  policyVersion: string;
  lines: {
    typeId: string;
    priceVersion: number;
    name: string;
    quantity: number;
    days: number;
    rental: number;
    deposit: number;
    available: number;
  }[];
};
// Auxiliary read models are projections; core transactional contracts above stay typed.
export type Row = Record<string, any>;
export type Dashboard = {
  total_assets: number;
  ready_assets: number;
  rented_assets: number;
  attention_assets: number;
  active_orders: number;
  held_orders: number;
  customers: number;
  open_tickets: number;
  overdue_items: number;
  rental_received?: number;
  deposits_held?: number;
  payroll_paid?: number;
  trend: { day: string; orders: number }[];
  categories: { category: string; total: number; ready: number; rented: number }[];
  asOf: string;
};
